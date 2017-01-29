package com.yosriz.rxrecyclerpagination;


import android.support.v7.widget.RecyclerView;

import rx.Emitter;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Cancellable;

class RxRecyclerViewEvents {

    static class ScrollEvent {
        int dx;
        int dy;
    }

    static Observable<ScrollEvent> scrollEvents(final RecyclerView rv) {

        return Observable.fromEmitter(new Action1<Emitter<ScrollEvent>>() {
            @Override
            public void call(final Emitter<ScrollEvent> scrollEventAsyncEmitter) {
                final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                        super.onScrolled(recyclerView, dx, dy);
                        ScrollEvent scrollEvent = new ScrollEvent();
                        scrollEvent.dx = dx;
                        scrollEvent.dy = dy;
                        scrollEventAsyncEmitter.onNext(scrollEvent);
                    }
                };

                scrollEventAsyncEmitter.setCancellation(new Cancellable() {
                    @Override
                    public void cancel() throws Exception {
                        rv.removeOnScrollListener(scrollListener);
                    }
                });

                rv.addOnScrollListener(scrollListener);
            }
        }, Emitter.BackpressureMode.LATEST);
    }
}
