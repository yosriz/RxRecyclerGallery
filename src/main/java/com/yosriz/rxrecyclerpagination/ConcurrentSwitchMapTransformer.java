package com.yosriz.rxrecyclerpagination;


import android.util.Log;

import java.util.LinkedList;

import rx.Emitter;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Cancellable;
import rx.functions.Func1;
import rx.subjects.BehaviorSubject;

class ConcurrentSwitchMapTransformer<T, R> implements Observable.Transformer<T, R> {

    private static final String TAG = ConcurrentSwitchMapTransformer.class.getSimpleName();
    private final int maxConcurrency;
    private final Func1<T, Observable<R>> func;

    private final Object lock = new Object();
    private LinkedList<ObservableWrapper<R>> loadingDataQueue = new LinkedList<>();

    public ConcurrentSwitchMapTransformer(int maxConcurrency, Func1<T, Observable<R>> func) {
        this.maxConcurrency = maxConcurrency;
        this.func = func;
    }

    @Override
    public Observable<R> call(final Observable<T> observable) {
        return observable.flatMap(new Func1<T, Observable<R>>() {
            @Override
            public Observable<R> call(final T t) {
                Log.d(TAG, String.format("data = %s, runningObservablesCount = %d , maxConcurrency = %d", t,
                        loadingDataQueue.size(), maxConcurrency));

                final ObservableWrapper<R> wrapper = new ObservableWrapper<>();
                wrapper.init(func.call(t)
                        .doOnTerminate(new Action0() {
                            @Override
                            public void call() {
                                Log.d(TAG, String.format("doOnTerminate - removing observable from queue for data = %s", t));
                                synchronized (lock) {
                                    loadingDataQueue.remove(wrapper);
                                }
                            }
                        }));

                synchronized (lock) {
                    loadingDataQueue.addFirst(wrapper);
                    if (loadingDataQueue.size() == maxConcurrency + 1) {
                        ObservableWrapper<R> observableToRemoveData = loadingDataQueue.removeLast();
                        Log.d(TAG, String.format("canceling oldest observable with data"));
                        observableToRemoveData.terminate();
                    }
                }
                return wrapper.getWrapperObservable();
            }
        });
    }

    private class ObservableWrapper<S> {

        private Observable<S> wrapper;
        private Subscription subscription;
        private Emitter<S> emitter;

        ObservableWrapper() {
        }

        public void init(final Observable<S> observable) {
            wrapper = Observable.fromEmitter(new Action1<Emitter<S>>() {

                @Override
                public void call(final Emitter<S> emitter) {
                    ObservableWrapper.this.emitter = emitter;
                    subscription = observable.subscribe(new Subscriber<S>() {
                        @Override
                        public void onCompleted() {
                            emitter.onCompleted();
                        }

                        @Override
                        public void onError(Throwable e) {
                            emitter.onError(e);
                        }

                        @Override
                        public void onNext(S s) {
                            emitter.onNext(s);
                        }
                    });
                    emitter.setCancellation(new Cancellable() {
                        @Override
                        public void cancel() throws Exception {
                            subscription.unsubscribe();
                        }
                    });
                }

            }, Emitter.BackpressureMode.BUFFER);
        }

        public Observable<S> getWrapperObservable() {
            return wrapper;
        }

        public void terminate() {
            subscription.unsubscribe();
            emitter.onCompleted();
        }

    }


    private class ObservableData {
        Observable<R> observable;
        BehaviorSubject<R> terminationSubject;
        T data;
    }
}
