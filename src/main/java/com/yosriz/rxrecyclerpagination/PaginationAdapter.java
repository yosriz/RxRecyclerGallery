package com.yosriz.rxrecyclerpagination;


import android.support.v7.widget.RecyclerView;
import android.util.Log;

import java.util.List;

public abstract class PaginationAdapter<T, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    public interface PaginationAdapterEvents {

        void onPageRequested(int pageNum);
    }
    private static final int VIEW_TYPE_LOADING = 0;

    private static final int VIEW_TYPE_DATA = 1;
    private final int pageSize;

    private PaginationAdapterEvents listener;

    private int currRequestedPage1;
    private int currRequestedPage2;
    //assuming 2 pages can be loaded at once when list is on the boundary between the 2
    private PageData loadedPageData1 = new PageData();
    private PageData loadedPageData2 = new PageData();
    private int dataCount = -1;

    protected class PageData {
        public int pageNum;
        public List<T> data;
    }

    public PaginationAdapter(int pageSize) {
        this.pageSize = pageSize;
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

    public void pageDataReady(List<T> data, int pageNumber) {
        Log.d("debug", "pageDataReady for page = " + pageNumber);
        loadedPageData2.data = loadedPageData1.data;
        loadedPageData2.pageNum = loadedPageData1.pageNum;

        loadedPageData1.data = data;
        int pageStart = pageNumber * pageSize;
        loadedPageData1.pageNum = pageNumber;
        notifyItemRangeChanged(pageStart, pageSize);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (isOutsideLoadedPagesBounds(position)) {
            bindLoadingViewHolder(holder, position);
            if (listener != null) {
                int newRequestedPage =  position / pageSize;
                if (newRequestedPage != currRequestedPage1 && newRequestedPage != currRequestedPage2){
                    currRequestedPage1 = newRequestedPage;
                    listener.onPageRequested(currRequestedPage1);
                }
            }
        } else {
            bindDataViewHolder(holder, position);
        }
    }

    @Override
    public int getItemCount() {
        return dataCount > 0 ? dataCount : Integer.MAX_VALUE;
    }

    protected PageData getCurrPage() {
        return loadedPageData1;
    }

    protected PageData getPrevPage() {
        return loadedPageData2;
    }

    protected int getPageSize() {
        return pageSize;
    }

    private boolean isOutsideLoadedPagesBounds(int position) {
        return isOutsidePage(position, loadedPageData1) && isOutsidePage(position, loadedPageData2);
    }

    private boolean isOutsidePage(int position, PageData pageData) {
        return pageData.data == null || position < pageSize * pageData.pageNum || position >= pageSize * (pageData.pageNum + 1);
    }

    protected abstract void bindDataViewHolder(RecyclerView.ViewHolder holder, int position);

    protected abstract void bindLoadingViewHolder(RecyclerView.ViewHolder holder, int position);

    @Override
    public int getItemViewType(int position) {
        return isOutsideLoadedPagesBounds(position) ? VIEW_TYPE_LOADING : VIEW_TYPE_DATA;
    }
}
