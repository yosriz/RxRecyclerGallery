package com.yosriz.rxrecyclerpagination;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.ViewGroup;

import java.util.List;

import rx.AsyncEmitter;
import rx.Observable;
import rx.functions.Action1;

public class RxPaginationAdapter<T, VH extends RecyclerView.ViewHolder> extends PaginationAdapter<T, VH> {

    private static final String TAG = RxPaginationAdapter.class.getSimpleName();

    public interface VIewBinding<T> {

        void onBindView(RecyclerView.ViewHolder holder, int position, T data);

    }

    public interface ViewHolderCreator<VH extends RecyclerView.ViewHolder> {

        VH onCreateViewHolder(ViewGroup parent, int viewType);

    }

    public static class DataCreationEventInfo<T> {

        public List<T> data;

        public int pageNumber;

    }

    private final VIewBinding<T> dataViewBinding;

    private final ViewHolderCreator<VH> viewHolderCreator;
    private final VIewBinding<T> loadingViewBinding;
    private final Observable<Integer> pageRequestsObservable;

    public RxPaginationAdapter(int pageSize, VIewBinding<T> dataViewBinding,
                               VIewBinding<T> loadingViewBinding, ViewHolderCreator<VH> viewHolderCreator) {
        super(pageSize);
        pageRequestsObservable = Observable.fromEmitter(new Action1<AsyncEmitter<Integer>>() {
            @Override
            public void call(final AsyncEmitter<Integer> asyncEmitter) {
                setPageEventsListener(new PaginationAdapterEvents() {
                    @Override
                    public void onPageRequested(int pageNum) {
                        Log.d("debug", "onPageRequested for page = " + pageNum);
                        asyncEmitter.onNext(pageNum);
                    }
                });
                asyncEmitter.setCancellation(new AsyncEmitter.Cancellable() {
                    @Override
                    public void cancel() throws Exception {
                        removePageEventsListener();
                    }
                });
            }
        }, AsyncEmitter.BackpressureMode.LATEST);

        this.dataViewBinding = dataViewBinding;
        this.loadingViewBinding = loadingViewBinding;
        this.viewHolderCreator = viewHolderCreator;
    }

    @Override
    protected void bindDataViewHolder(RecyclerView.ViewHolder holder, int position) {
        T data = getDataFromPage(position, getCurrPage());
        if (data == null) {
            data = getDataFromPage(position, getPrevPage());
        }
        if (data != null) {
            this.dataViewBinding.onBindView(holder, position, data);
        }else{
            Log.e(TAG, String.format("ERROR! cannot find data for location %d", position));
        }
    }

    private T getDataFromPage(int position, PageData pageData) {
        T data = null;
        int pageStartIndex = pageData.pageNum * getPageSize();
        int pageEndIndex = pageStartIndex + getPageSize();
        if (position >= pageStartIndex && position < pageEndIndex) {
            data = pageData.data.get(position - pageStartIndex);
        }
        return data;
    }

    @Override
    protected void bindLoadingViewHolder(RecyclerView.ViewHolder holder, int position) {
        this.loadingViewBinding.onBindView(holder, position, null);
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        return this.viewHolderCreator.onCreateViewHolder(parent, viewType);
    }

    public Observable<Integer> getPageRequestsObservable() {
        return pageRequestsObservable;
    }
}
