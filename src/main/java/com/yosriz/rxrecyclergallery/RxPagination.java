package com.yosriz.rxrecyclergallery;


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
        private static final int DEFAULT_THROTTLING_INTERVAL_MILLIS = 500;
        private static final int DEFAULT_CACHE_SIZE = 50;
        private static final int DEFAULT_MAX_CONCURRENCY = 2;

        private final RecyclerView recyclerView;
        private int pageSize = 0;
        private ViewHandlers<T, VH> viewHandlers;
        private DataLoader dataLoader;
        private long intervalMillis = DEFAULT_THROTTLING_INTERVAL_MILLIS;
        private int cacheSize = DEFAULT_CACHE_SIZE;
        private int concurrencyLevel = DEFAULT_MAX_CONCURRENCY;

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

        public Builder<T, VH> setThrottlingInterval(long intervalMillis) {
            this.intervalMillis = intervalMillis;
            return this;
        }

        public Builder<T, VH> setCacheSize(int size) {
            this.cacheSize = size;
            return this;
        }

        public Builder<T, VH> setConcurrencyLevel(int concurrencyLevel) {
            this.concurrencyLevel = concurrencyLevel;
            return this;
        }

        public RxPaginationAdapter<T, VH> build() {
            String errors = "";
            if (viewHandlers == null) {
                errors += "viewHandlers can't be null!\n";
            }
            if (pageSize <= 0) {
                errors += "pageSize must be positive value!\n";
            }
            if (dataLoader == null) {
                errors += "dataLoader can't be null!\n";
            }
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException(errors);
            }
            RxPaginationAdapter<T, VH> adapter = new RxPaginationAdapter<>(recyclerView, pageSize, viewHandlers, dataLoader, intervalMillis, cacheSize, concurrencyLevel);
            recyclerView.setAdapter(adapter);
            return adapter;
        }
    }
}