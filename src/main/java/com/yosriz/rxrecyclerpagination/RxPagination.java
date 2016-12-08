package com.yosriz.rxrecyclerpagination;


import android.support.v7.widget.RecyclerView;

import java.util.List;

import rx.Observable;

public final class RxPagination<T, VH extends RecyclerView.ViewHolder> {

    private final RxPaginationAdapter<T, VH> paginationAdapter;

    private RxPagination(RxPaginationAdapter<T, VH> paginationAdapter) {
        this.paginationAdapter = paginationAdapter;
    }

    public Observable<Integer> getPageRequestsObservable() {
        return paginationAdapter.getPageRequestsObservable();
    }

    public void pageDataReady(List<T> data, int pageNumber) {
        paginationAdapter.pageDataReady(data, pageNumber);
    }

    public static <T, VH extends RecyclerView.ViewHolder> Builder<T, VH> with(RecyclerView recyclerView) {
        return new Builder<>(recyclerView);
    }

    public static class Builder<T, VH extends RecyclerView.ViewHolder> {
        private final RecyclerView recyclerView;
        private int pageSize;

        private RxPaginationAdapter.VIewBinding<T> dataViewBinding;
        private RxPaginationAdapter.ViewHolderCreator<VH> viewHolderCreator;
        private RxPaginationAdapter.VIewBinding<T> loadingViewBinding;

        private Builder(RecyclerView recyclerView) {
            this.recyclerView = recyclerView;
        }

        public Builder<T, VH> setViewHolderCreator(RxPaginationAdapter.ViewHolderCreator<VH> viewHolderCreator) {
            this.viewHolderCreator = viewHolderCreator;
            return this;
        }

        public Builder<T, VH> setLoadingViewBinding(RxPaginationAdapter.VIewBinding loadingViewBinding) {
            this.loadingViewBinding = loadingViewBinding;
            return this;
        }

        public Builder<T, VH> setPageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public Builder<T, VH> setDataViewBinding(RxPaginationAdapter.VIewBinding dataViewBinding) {
            this.dataViewBinding = dataViewBinding;
            return this;
        }

        public RxPaginationAdapter<T, VH> build() {
            RxPaginationAdapter<T, VH> adapter = new RxPaginationAdapter<>(pageSize,
                    dataViewBinding, loadingViewBinding, viewHolderCreator);
            recyclerView.setAdapter(adapter);
            return adapter;
        }
    }
}