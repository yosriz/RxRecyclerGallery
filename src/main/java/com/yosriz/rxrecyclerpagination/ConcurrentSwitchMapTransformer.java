package com.yosriz.rxrecyclerpagination;


import java.util.concurrent.atomic.AtomicInteger;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

class ConcurrentSwitchMapTransformer<T, R> implements Observable.Transformer<T, R> {

    private final int maxConcurrency;
    private final Func1<T, Observable<R>> mapperFunc;

    ConcurrentSwitchMapTransformer(int maxConcurrency, Func1<T, Observable<R>> mapperFunc) {
        this.maxConcurrency = maxConcurrency;
        this.mapperFunc = mapperFunc;
    }

    public Observable<R> call(final Observable<T> observable) {
        return Observable.defer(
                new Func0<Observable<R>>() {
                    @Override
                    public Observable<R> call() {
                        final AtomicInteger ingress = new AtomicInteger();
                        final Subject<Integer, Integer> cancel =
                                PublishSubject.<Integer>create().toSerialized();

                        return observable.flatMap(
                                new Func1<T, Observable<R>>() {
                                    @Override
                                    public Observable<R> call(T t) {
                                        final int id = ingress.getAndIncrement();
                                        return mapperFunc.call(t)
                                                .doOnSubscribe(new Action0() {
                                                    @Override
                                                    public void call() {
                                                        cancel.onNext(id);
                                                    }
                                                })
                                                .takeUntil(cancel.filter(
                                                        new Func1<Integer, Boolean>() {
                                                            @Override
                                                            public Boolean call(Integer integer) {
                                                                return integer == id + maxConcurrency;
                                                            }
                                                        }));
                                    }
                                });
                    }
                });
    }
}
