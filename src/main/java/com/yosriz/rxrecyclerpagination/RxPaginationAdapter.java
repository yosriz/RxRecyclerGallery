package com.yosriz.rxrecyclerpagination;


import android.support.annotation.NonNull;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.LruCache;
import android.view.ViewGroup;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class RxPaginationAdapter<T, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    private static final int VIEW_TYPE_LOADING = 0;
    private static final int VIEW_TYPE_DATA = 1;

    private final int pageSize;
    private final RecyclerView recyclerView;
    private final Object lock = new Object();
    private final RxPagination.ViewHandlers<T, VH> viewHandlers;
    private final LruCache<Integer, List<T>> pagesCache;
    private final Integer[] visiblePages = new Integer[2];
    private final RxPagination.DataLoader dataLoader;
    private final Set<Integer> currentPagesLoadInProgress;
    private final long intervalMillis;
    private final int concurrencyLevel;
    private Observable<Integer[]> visiblePagesEvents;
    private CompositeSubscription subscriptions;
    private int dataCount = -1;

    private class PageData {
        int pageNum;
        List<T> data;
    }

    RxPaginationAdapter
            (RecyclerView recyclerView, int pageSize, RxPagination.ViewHandlers<T, VH> viewHandlers, RxPagination.DataLoader dataLoader,
             long intervalMillis, int cacheSize, int concurrencyLevel) {
        this.pageSize = pageSize;
        this.dataLoader = dataLoader;
        this.viewHandlers = viewHandlers;
        this.intervalMillis = intervalMillis;
        this.concurrencyLevel = concurrencyLevel;
        this.pagesCache = new LruCache<>(cacheSize);
        this.currentPagesLoadInProgress = Collections.synchronizedSet(new HashSet<Integer>());
        this.recyclerView = recyclerView;

        initObservables();

        setHasStableIds(true);
    }

    private void initObservables() {
        subscriptions = new CompositeSubscription();
        visiblePagesEvents = createSharedVisiblePagesEvent(recyclerView);
        fetchVisiblePage();
        prefetchPreviousPage();
        prefetchNextPage();
    }

    @Override
    public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);

        subscriptions.unsubscribe();
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);

        if (subscriptions.isUnsubscribed()) {
            subscriptions = new CompositeSubscription();
            initObservables();
        }
    }

    private Observable<Integer[]> createSharedVisiblePagesEvent(RecyclerView recyclerView) {
        return RxRecyclerViewEvents.scrollEvents(recyclerView)
                .throttleLast(intervalMillis, TimeUnit.MILLISECONDS)
                .map(new Func1<RxRecyclerViewEvents.ScrollEvent, Integer[]>() {
                    @Override
                    public Integer[] call(RxRecyclerViewEvents.ScrollEvent scrollEvent) {
                        return findVisiblePages();
                    }
                });
    }

    private void fetchVisiblePage() {
        Subscription subscribe = visiblePagesEvents
                .flatMap(new Func1<Integer[], Observable<Integer>>() {
                    @Override
                    public Observable<Integer> call(Integer[] integers) {
                        Logger.d("visiblePagesSingleEvents for pages " + integers[0] + ',' + integers[1]);
                        return Observable.from(integers);
                    }
                })
                .filter(new Func1<Integer, Boolean>() {
                    @Override
                    public Boolean call(Integer pageNum) {
                        return tryAcquireLockFoVisiblePageTask(pageNum);
                    }
                })
                .compose(new ConcurrentSwitchMapTransformer<>(concurrencyLevel, new Func1<Integer, Observable<PageData>>() {
                    @Override
                    public Observable<PageData> call(Integer pageNum) {
                        return loadDataObservable(pageNum);
                    }
                }))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(loadDataSubscriber);
        subscriptions.add(subscribe);
    }

    @NonNull
    private Boolean tryAcquireLockFoVisiblePageTask(Integer pageNum) {
        synchronized (lock) {
            boolean canLoad = !isPageLoadingOrLoaded(pageNum);
            if (canLoad) {
                currentPagesLoadInProgress.add(pageNum);
            }
            Logger.d("visiblePagesSingleEvents " + (canLoad ? "can load page = " : "CANNOT load page = ") + pageNum);
            return canLoad;
        }
    }

    private void prefetchNextPage() {
        Subscription subscribe = visiblePagesEvents
                .map(new Func1<Integer[], Integer>() {
                    @Override
                    public Integer call(Integer[] pages) {
                        return pages[1] + 1;
                    }
                })
                .filter(new Func1<Integer, Boolean>() {
                    @Override
                    public Boolean call(Integer pageNum) {
                        return tryAcquireLockForNextPageTask(pageNum);
                    }
                })
                .switchMap(new Func1<Integer, Observable<PageData>>() {
                    @Override
                    public Observable<PageData> call(Integer pageNum) {
                        return loadDataObservable(pageNum);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(loadDataSubscriber);
        subscriptions.add(subscribe);
    }

    @NonNull
    private Boolean tryAcquireLockForNextPageTask(Integer pageNum) {
        synchronized (lock) {
            boolean canLoad = (dataCount < 0 || pageNum < dataCount / RxPaginationAdapter.this.pageSize)
                    && !isPageLoadingOrLoaded(pageNum);
            if (canLoad) {
                currentPagesLoadInProgress.add(pageNum);
            }
            Logger.d("next data " + (canLoad ? "can load page = " : "CANNOT load page = ") + pageNum);
            return canLoad;
        }
    }

    private void prefetchPreviousPage() {
        Subscription subscribe = visiblePagesEvents
                .map(new Func1<Integer[], Integer>() {
                    @Override
                    public Integer call(Integer[] pages) {
                        return pages[0] - 1;
                    }
                })
                .filter(new Func1<Integer, Boolean>() {
                    @Override
                    public Boolean call(Integer pageNum) {
                        return tryAcquireLockForPrevPageTask(pageNum);
                    }
                })
                .switchMap(new Func1<Integer, Observable<PageData>>() {
                    @Override
                    public Observable<PageData> call(Integer pageNum) {
                        return loadDataObservable(pageNum);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(loadDataSubscriber);
        subscriptions.add(subscribe);
    }

    @NonNull
    private Boolean tryAcquireLockForPrevPageTask(Integer pageNum) {
        synchronized (lock) {
            boolean canLoad = pageNum >= 0 && !isPageLoadingOrLoaded(pageNum);
            if (canLoad) {
                currentPagesLoadInProgress.add(pageNum);
            }
            Logger.d("prev data " + (canLoad ? "can load page = " : "CANNOT load page = ") + pageNum);
            return canLoad;
        }
    }

    private boolean isPageLoadingOrLoaded(Integer pageNum) {
        boolean loadInProgress = currentPagesLoadInProgress.contains(pageNum);
        Logger.d("page %d is %s loading.", pageNum, loadInProgress ? "" : "NOT");
        return loadInProgress || isPageLoaded(pageNum, true);
    }

    @SuppressWarnings("unchecked")
    private Observable<PageData> loadDataObservable(final Integer pageNum) {
        return dataLoader.loadData(pageNum)
                .map(new Func1<List<T>, PageData>() {
                    @Override
                    public PageData call(List<T> data) {
                        synchronized (lock) {
                            pagesCache.put(pageNum, data);
                            currentPagesLoadInProgress.remove(pageNum);
                        }
                        PageData pageData = new PageData();
                        pageData.pageNum = pageNum;
                        pageData.data = data;
                        return pageData;
                    }
                })
                .doOnTerminate(new Action0() {
                    @Override
                    public void call() {
                        Logger.d("loadDataObservable doOnTerminate for page = " + pageNum);
                        currentPagesLoadInProgress.remove(pageNum);
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        Logger.d("loadDataObservable doOnUnsubscribe for page = " + pageNum);
                        currentPagesLoadInProgress.remove(pageNum);
                    }
                })
                .onErrorReturn(new Func1<Throwable, PageData>() {
                    @Override
                    public PageData call(Throwable throwable) {
                        Logger.e(throwable, "loadDataObservable onErrorReturn\n");
                        return null;
                    }
                })
                .subscribeOn(Schedulers.io());
    }

    private Subscriber<PageData> loadDataSubscriber = new Subscriber<PageData>() {

        @Override
        public void onCompleted() {
            Logger.d("loadDataSubscriber - onCompleted\n");
        }

        @Override
        public void onError(Throwable e) {
            Logger.wtf(e, "loadDataSubscriber onError\n");
        }

        @Override
        public void onNext(PageData pageData) {
            if (pageData != null) {
                dataReady(pageData);
            }
        }
    };

    private Integer[] findVisiblePages() {
        try {
            int firstVisibleIndex = 0, lastVisibleIndex = 0;
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            if (layoutManager instanceof GridLayoutManager) {
                GridLayoutManager glm = (GridLayoutManager) layoutManager;
                firstVisibleIndex = glm.findFirstVisibleItemPosition();
                lastVisibleIndex = glm.findLastVisibleItemPosition();
            } else if (layoutManager instanceof LinearLayoutManager) {
                LinearLayoutManager llm = (LinearLayoutManager) layoutManager;
                firstVisibleIndex = llm.findFirstVisibleItemPosition();
                lastVisibleIndex = llm.findLastVisibleItemPosition();
            } else if (layoutManager instanceof StaggeredGridLayoutManager) {
                StaggeredGridLayoutManager sglm = (StaggeredGridLayoutManager) layoutManager;
                int[] firstPositions = sglm.findFirstVisibleItemPositions(null);
                Arrays.sort(firstPositions);
                firstVisibleIndex = firstPositions[0];
                int[] lastPositions = sglm.findLastVisibleItemPositions(null);
                Arrays.sort(lastPositions);
                lastVisibleIndex = lastPositions[0];
            } else {
                Logger.wtf("Not supported LayoutManager!! currently only supporting Android native Layout Managers.\n");
            }

            visiblePages[0] = firstVisibleIndex / pageSize;
            visiblePages[1] = lastVisibleIndex / pageSize;
            Logger.d("findVisiblePages pages are = [%d - %d]", visiblePages[0], visiblePages[1]);
        } catch (Exception e) {
            visiblePages[0] = 0;
            visiblePages[1] = 0;
        }

        return visiblePages;
    }

    public void setDataCount(int dataCount) {
        this.dataCount = dataCount;
    }

    private void dataReady(PageData pageData) {
        Logger.d(String.format("dataReady for page = %s", pageData.pageNum));
        pagesCache.put(pageData.pageNum, pageData.data);

        int pageStart = pageData.pageNum * pageSize;
        notifyItemRangeChanged(pageStart, pageSize);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        int newRequestedPage = position / pageSize;

        if (isPageLoaded(newRequestedPage)) {
            bindDataViewHolder(holder, getData(position, newRequestedPage), position);
        }
    }

    private T getData(int position, int pageNum) {
        List<T> pageData = pagesCache.get(pageNum);
        if (pageData != null) {
            int index = position - (pageNum * pageSize);
            return pageData.get(index);
        }
        return null;
    }

    private boolean isPageLoaded(int pageNum) {
        return isPageLoaded(pageNum, false);
    }

    private boolean isPageLoaded(int pageNum, boolean log) {
        boolean isLoaded = pagesCache.get(pageNum) != null;
        if (log) {
            Logger.d("page %d is %s loaded.", pageNum, isLoaded ? "" : "NOT");
        }
        return isLoaded;
    }

    @Override
    public int getItemCount() {
        return dataCount > 0 ? dataCount : Integer.MAX_VALUE;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return isPageLoaded(position / pageSize) ? VIEW_TYPE_LOADING : VIEW_TYPE_DATA;
    }

    private void bindDataViewHolder(RecyclerView.ViewHolder holder, T dataItem, int position) {
        if (dataItem != null) {
            this.viewHandlers.onBindDataView(holder, position, dataItem);
        }
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_DATA) {
            return this.viewHandlers.onCreateDataViewHolder(parent, viewType);
        } else {
            return this.viewHandlers.onCreateLoadingViewHolder(parent, viewType);
        }
    }
}
