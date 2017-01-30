package com.yosriz.rxrecyclerpagination;


import android.support.v7.widget.RecyclerView;
import android.view.ViewGroup;

import java.util.List;

import rx.Observable;

public final class RxPagination {

    public interface ViewHandlers<T, VH extends RecyclerView.ViewHolder> {

        void onBindDataView(RecyclerView.ViewHolder holder, int position, T data);

        VH onCreateLoadingViewHolder(ViewGroup parent, int viewType);

        VH onCreateDataViewHolder(ViewGroup parent, int viewType);
    }

    public interface DataLoader<T> {
        Observable<List<T>> loadData(int pageNum);
    }

    public static <T, VH extends RecyclerView.ViewHolder> Builder<T, VH> with(RecyclerView recyclerView) {
        return new Builder<>(recyclerView);
    }

    public static class Builder<T, VH extends RecyclerView.ViewHolder> {
        private final RecyclerView recyclerView;
        private int pageSize;
        private ViewHandlers<T, VH> viewHandlers;
        private DataLoader dataLoader;

        private Builder(RecyclerView recyclerView) {
            this.recyclerView = recyclerView;
        }

        public Builder<T, VH> setViewHandlers(ViewHandlers<T, VH> viewHandlers) {
            this.viewHandlers = viewHandlers;
            return this;
        }

        public Builder<T, VH> setPageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public Builder<T, VH> setDataLoader(DataLoader dataLoader) {
            this.dataLoader = dataLoader;
            return this;
        }

        public RxPaginationAdapter<T, VH> build() {
            RxPaginationAdapter<T, VH> adapter = new RxPaginationAdapter<>(recyclerView, pageSize, viewHandlers, dataLoader);
            recyclerView.setAdapter(adapter);
            return adapter;
        }
    }
}