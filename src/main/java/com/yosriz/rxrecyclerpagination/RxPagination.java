package com.yosriz.rxrecyclerpagination;


import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

import java.util.List;

import rx.Observable;

public final class RxPagination<T, VH extends RecyclerView.ViewHolder> {

    private final RxPaginationAdapter<T, VH> paginationAdapter;

    public interface VIewBinding<T> {

        void onBindView(RecyclerView.ViewHolder holder, int position, T data);
    }

    public interface ViewHolderCreator<VH extends RecyclerView.ViewHolder> {

        VH onCreateViewHolder(ViewGroup parent, int viewType);
    }

    public interface DataLoader<T> {
        Observable<List<T>> loadData(int pageNum);
    }

    private RxPagination(RxPaginationAdapter<T, VH> paginationAdapter) {
        this.paginationAdapter = paginationAdapter;
    }

    public static <T, VH extends RecyclerView.ViewHolder> Builder<T, VH> with(RecyclerView recyclerView) {
        return new Builder<>(recyclerView);
    }

    public static class Builder<T, VH extends RecyclerView.ViewHolder> {
        private final RecyclerView recyclerView;
        private int pageSize;
        private VIewBinding<T> dataViewBinding;
        private ViewHolderCreator<VH> viewHolderCreator;
        private VIewBinding<T> loadingViewBinding;
        private DataLoader dataLoader;

        private Builder(RecyclerView recyclerView) {
            this.recyclerView = recyclerView;
        }

        public Builder<T, VH> setViewHolderCreator(ViewHolderCreator<VH> viewHolderCreator) {
            this.viewHolderCreator = viewHolderCreator;
            return this;
        }

        public Builder<T, VH> setLoadingViewBinding(VIewBinding loadingViewBinding) {
            this.loadingViewBinding = loadingViewBinding;
            return this;
        }

        public Builder<T, VH> setPageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public Builder<T, VH> setDataViewBinding(VIewBinding dataViewBinding) {
            this.dataViewBinding = dataViewBinding;
            return this;
        }

        public Builder<T, VH> setDataLoader(DataLoader dataLoader) {
            this.dataLoader = dataLoader;
            return this;
        }

        public RxPaginationAdapter<T, VH> build() {
            RxPaginationAdapter<T, VH> adapter = new RxPaginationAdapter<>(recyclerView, pageSize,
                    dataViewBinding, loadingViewBinding, viewHolderCreator, dataLoader);
            recyclerView.setAdapter(adapter);
            return adapter;
        }
    }
}