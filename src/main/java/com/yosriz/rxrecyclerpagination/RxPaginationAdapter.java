package com.yosriz.rxrecyclerpagination;


import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.Log;
import android.util.LruCache;
import android.view.ViewGroup;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class RxPaginationAdapter<T, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    private static final String TAG = RxPaginationAdapter.class.getSimpleName();
    public static final int LOAD_DATA_RETRY_COUNT = 2;
    private static final long LOADING_TIMEOUT_MILLIS = 5000;
    private final Object maxConcurrentLock = new Object();
    LinkedList<Observable> loadingDataQueue = new LinkedList<Observable>();

    public interface PaginationAdapterEvents {

        void onPageRequested(int pageNum);
    }

    private static final int VIEW_TYPE_LOADING = 0;
    private static final int VIEW_TYPE_DATA = 1;
    private static final int THROTTLING_INTERVAL_MILLIS = 500;
    private static final int CACHE_SIZE = 50;
    private static final int MAX_CONCURRENCY = 5;
    private final int pageSize;

    private PaginationAdapterEvents listener;
    private int dataCount = -1;
    private RecyclerView recyclerView;
    private LruCache<Integer, List<T>> pagesCache;
    private Integer[] visiblePages = new Integer[2];
    private RxPagination.DataLoader dataLoader;
    private Set<Integer> currentPagesLoadInProgress;
    private final Object lock = new Object();
    private final RxPagination.VIewBinding<T> dataViewBinding;
    private final RxPagination.ViewHolderCreator<VH> viewHolderCreator;
    private final RxPagination.VIewBinding<T> loadingViewBinding;

    protected class PageData {
        public int pageNum;
        public List<T> data;
    }

    public RxPaginationAdapter(RecyclerView recyclerView, int pageSize, RxPagination.VIewBinding<T> dataViewBinding,
                               RxPagination.VIewBinding<T> loadingViewBinding, RxPagination.ViewHolderCreator<VH> viewHolderCreator,
                               RxPagination.DataLoader dataLoader) {
        this.pageSize = pageSize;
        this.dataLoader = dataLoader;
        this.dataViewBinding = dataViewBinding;
        this.viewHolderCreator = viewHolderCreator;
        this.loadingViewBinding = loadingViewBinding;

        pagesCache = new LruCache<>(CACHE_SIZE);
        currentPagesLoadInProgress = Collections.synchronizedSet(new HashSet<Integer>());
        this.recyclerView = recyclerView;


        Observable<Integer[]> visiblePagesEvents =
                RxRecyclerViewEvents.scrollEvents(recyclerView)
                        .throttleLast(THROTTLING_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                        .map(new Func1<RxRecyclerViewEvents.ScrollEvent, Integer[]>() {
                            @Override
                            public Integer[] call(RxRecyclerViewEvents.ScrollEvent scrollEvent) {
                                return findVisiblePages();
                            }
                        });

        Observable<Integer> visiblePagesSingleEvents = visiblePagesEvents
                .flatMap(new Func1<Integer[], Observable<Integer>>() {
                    @Override
                    public Observable<Integer> call(Integer[] integers) {
                        Log.d("TAG", "visiblePagesSingleEvents for pages " + integers[0] + ',' + integers[1]);
                        return Observable.from(integers);
                    }
                });

        visiblePagesSingleEvents
                .filter(new Func1<Integer, Boolean>() {
                    @Override
                    public Boolean call(Integer pageNum) {
                        synchronized (lock) {
                            boolean canLoad = !isPageLoadingOrLoaded(pageNum);
                            if (canLoad) {
                                currentPagesLoadInProgress.add(pageNum);
                            }
                            Log.d("TAG", "visiblePagesSingleEvents " + (canLoad ? "can load page = " : "CANNOT load page = ") + pageNum);
                            return canLoad;
                        }
                    }
                })
                .subscribeOn(Schedulers.io())
                .compose(
                        new ConcurrentSwitchMapTransformer<>(MAX_CONCURRENCY, new Func1<Integer, Observable<PageData>>() {
                            @Override
                            public Observable<PageData> call(Integer pageNum) {
                                return loadDataObservable(pageNum);
                            }
                        }))
               /* .flatMap(new Func1<Integer, Observable<PageData>>() {
                    @Override
                    public Observable<PageData> call(final Integer pageNum) {
                        Log.d(TAG, String.format("creating loadDataObservable -  for pageNum = %d, runningObservablesCount = %d , maxConcurrency = %d", pageNum,
                                loadingDataQueue.size(), MAX_CONCURRENCY));
                        final Observable<PageData> loadDataObservable = loadDataObservable(pageNum);
                        synchronized (maxConcurrentLock) {
                            loadingDataQueue.addFirst(loadDataObservable);
                            if (loadingDataQueue.size() == MAX_CONCURRENCY + 1) {
                                Log.d(TAG, String.format("creating loadDataObservable - canceling oldest observable"));
                                loadingDataQueue.removeLast().takeUntil(Observable.just(null));
                            }
                        }
                        return loadDataObservable.doOnTerminate(new Action0() {
                            @Override
                            public void call() {
                                Log.d(TAG, String.format("creating loadDataObservable - removing #%d observable from queue.", pageNum));
                                synchronized (maxConcurrentLock) {
                                    loadingDataQueue.remove(loadDataObservable);
                                }
                            }
                        });
                    }
                })*/
                //.compose(new ConcurrentSwitchMapTransformer<PageData>(MAX_CONCURRENCY))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(loadDataSubscriber);
        visiblePagesEvents
                .map(new Func1<Integer[], Integer>() {
                    @Override
                    public Integer call(Integer[] pages) {
                        return pages[0] - 1;
                    }
                })
                .subscribeOn(Schedulers.io())
                .filter(new Func1<Integer, Boolean>() {
                    @Override
                    public Boolean call(Integer pageNum) {
                        synchronized (lock) {
                            boolean canLoad = pageNum >= 0 && !isPageLoadingOrLoaded(pageNum);
                            if (canLoad) {
                                currentPagesLoadInProgress.add(pageNum);
                            }
                            Log.d("TAG", "prev data " + (canLoad ? "can load page = " : "CANNOT load page = ") + pageNum);
                            return canLoad;
                        }
                    }
                })
                .switchMap(new Func1<Integer, Observable<PageData>>() {
                    @Override
                    public Observable<PageData> call(Integer pageNum) {
                        return loadDataObservable(pageNum);
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(loadDataSubscriber);

        visiblePagesEvents
                .map(new Func1<Integer[], Integer>() {
                    @Override
                    public Integer call(Integer[] pages) {
                        return pages[1] + 1;
                    }
                })
                .subscribeOn(Schedulers.io())
                .filter(new Func1<Integer, Boolean>() {
                    @Override
                    public Boolean call(Integer pageNum) {
                        synchronized (lock) {
                            boolean canLoad = (dataCount < 0 || pageNum < dataCount / RxPaginationAdapter.this.pageSize)
                                    && !isPageLoadingOrLoaded(pageNum);
                            if (canLoad) {
                                currentPagesLoadInProgress.add(pageNum);
                            }
                            Log.d("TAG", "next data " + (canLoad ? "can load page = " : "CANNOT load page = ") + pageNum);
                            return canLoad;
                        }
                    }
                })
                .switchMap(new Func1<Integer, Observable<PageData>>() {
                    @Override
                    public Observable<PageData> call(Integer pageNum) {
                        return loadDataObservable(pageNum);
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(loadDataSubscriber);

        setHasStableIds(true);
    }

    private boolean isPageLoadingOrLoaded(Integer pageNum) {
        boolean loadInProgress = currentPagesLoadInProgress.contains(pageNum);
        Log.d(TAG, String.format("page %d is %s loading.", pageNum, loadInProgress ? "" : "NOT"));
        return loadInProgress || isPageLoaded(pageNum, true);
    }

    @SuppressWarnings("unchecked")
    private Observable<PageData> loadDataObservable(final Integer pageNum) {
        return dataLoader.loadData(pageNum)
                .timeout(LOADING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .retry(LOAD_DATA_RETRY_COUNT)
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
                .doOnNext(new Action1<PageData>() {
                    @Override
                    public void call(PageData pageData) {
                        Log.d(TAG, "loadDataObservable doOnNext for page = " + pageData.pageNum);
                    }
                })
                .doOnTerminate(new Action0() {
                    @Override
                    public void call() {
                        synchronized (lock) {
                            Log.d(TAG, "loadDataObservable doOnTerminate for page = " + pageNum);
                            currentPagesLoadInProgress.remove(pageNum);
                        }
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        Log.d(TAG, "loadDataObservable doOnUnsubscribe for page = " + pageNum);
                        currentPagesLoadInProgress.remove(pageNum);
                    }
                })
                .onErrorReturn(new Func1<Throwable, PageData>() {
                    @Override
                    public PageData call(Throwable throwable) {
                        Log.e(TAG, "loadDataObservable onErrorReturn\n", throwable);
                        return null;
                    }
                })
                .subscribeOn(Schedulers.io());
    }

    private Subscriber<PageData> loadDataSubscriber = new Subscriber<PageData>() {

        @Override
        public void onCompleted() {
            Log.d(TAG, "loadDataSubscriber - onCompleted\n");
        }

        @Override
        public void onError(Throwable e) {
            Log.e(TAG, "loadDataSubscriber onError\n", e);
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
            }

            visiblePages[0] = firstVisibleIndex / pageSize;
            visiblePages[1] = lastVisibleIndex / pageSize;
            Log.d(TAG, String.format("findVisiblePages pages are = [%d - %d]", visiblePages[0], visiblePages[1]));
        } catch (Exception e) {
            visiblePages[0] = 0;
            visiblePages[1] = 0;
        }

        return visiblePages;
    }

    public void setPageEventsListener(PaginationAdapterEvents listener) {
        this.listener = listener;
    }

    public void removePageEventsListener() {
        this.listener = null;
    }

    public void setDataCount(int dataCount) {
        this.dataCount = dataCount;
    }

    private void dataReady(PageData pageData) {
        Log.d(TAG, String.format("dataReady for page = %s", pageData.pageNum));
        pagesCache.put(pageData.pageNum, pageData.data);

        int pageStart = pageData.pageNum * pageSize;
        notifyItemRangeChanged(pageStart, pageSize);
    }

   /* public void pageDataReady(List<T> data, int pageNumber) {
        Log.d(TAG, String.format("pageDataReady for page = %s", pageNumber));
        printLastRequested("pageDataReady");
        if (isCurrentlyLoading(pageNumber)) {
            PageData pageData = new PageData();
            pageData.data = data;
            pageData.pageNum = pageNumber;
            //loadedPages.add(pageData);
            pagesCache.put(pageNumber, data);

            int pageStart = pageNumber * pageSize;
            notifyItemRangeChanged(pageStart, pageSize);
        }
    }*/

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        int newRequestedPage = position / pageSize;
        //Log.d(TAG, String.format("onBindViewHolder for position = %d, page = %d", position, newRequestedPage));
        //printLoadedPages("onBindViewHolder");

        if (isPageLoaded(newRequestedPage)) {
            bindDataViewHolder(holder, getData(position, newRequestedPage), position);
        } else {
            bindLoadingViewHolder(holder, position);
        }
    }

    private void printLastRequested(String prefix) {
    }

    private void printLoadedPages(String prefix) {
        String nums = prefix + " - loadedPages = ";
        for (Integer pageNum : pagesCache.snapshot().keySet()) {
            nums += pageNum + ",";
        }
        Log.d(TAG, nums);
    }

    private T getData(int position, int pageNum) {
        List<T> pageData = pagesCache.get(pageNum);
        if (pageData != null) {
            int index = position - (pageNum * pageSize);
            return pageData.get(index);
        }
        //Log.e(TAG, String.format("ERROR! cannot find data for location %d", position));
        return null;
    }
/*
    private boolean isCurrentlyLoading(int pageNum) {
        for (Integer loadedPage : lastRequestedPages) {
            if (pageNum == loadedPage)
                return true;
        }
        return false;
    }*/

    private boolean isPageLoaded(int pageNum) {
        return isPageLoaded(pageNum, false);
    }

    private boolean isPageLoaded(int pageNum, boolean log) {
        boolean isLoaded = pagesCache.get(pageNum) != null;
        if (log) {
            Log.d(TAG, String.format("page %d is %s loaded.", pageNum, isLoaded ? "" : "NOT"));
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

    protected int getPageSize() {
        return pageSize;
    }

    @Override
    public int getItemViewType(int position) {
        return isPageLoaded(position / pageSize) ? VIEW_TYPE_LOADING : VIEW_TYPE_DATA;
    }

    protected void bindDataViewHolder(RecyclerView.ViewHolder holder, T dataItem, int position) {
        if (dataItem != null) {
            this.dataViewBinding.onBindView(holder, position, dataItem);
        }
    }

    protected void bindLoadingViewHolder(RecyclerView.ViewHolder holder, int position) {
        this.loadingViewBinding.onBindView(holder, position, null);
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        return this.viewHolderCreator.onCreateViewHolder(parent, viewType);
    }
}
