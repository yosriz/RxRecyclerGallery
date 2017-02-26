package com.yosriz.rxrecyclergallery;

import android.support.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

import static com.yosriz.rxrecyclergallery.TestLogger.log;

@RunWith(Parameterized.class)
public class ConcurrentSwitchMapTransformerTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {2, 300L, new Long[]{100L, 100L, 100L, 100L, 100L},
                        new Long[]{4L, 5L}},
                {3, 300L, new Long[]{100L, 100L, 100L, 100L, 100L},
                        new Long[]{1L, 2L, 3L, 4L, 5L}},
                {1, 50L, new Long[]{100L, 100L, 100L, 100L, 100L},
                        new Long[]{1L, 2L, 3L, 4L, 5L}},
                {1, 150L, new Long[]{100L, 100L, 100L, 100L, 100L},
                        new Long[]{5L}}

        });
    }

    private int cacheSIze;
    private final Long taskLength;
    private Long[] intervals;
    private Long[] expectedValues;

    public ConcurrentSwitchMapTransformerTest(int cacheSIze, Long taskLength, Long[] intervals, Long[] expectedValues) {
        this.cacheSIze = cacheSIze;
        this.intervals = intervals;
        this.expectedValues = expectedValues;
        this.taskLength = taskLength;
    }

    @Test
    public void run_multiple_tasks() {
        log("test started");
        Observable<Long> intervalObservable = Observable.from(intervals);
        Observable<Long> counterObservable = intervalObservable
                .map(new Func1<Long, Long>() {
                    @Override
                    public Long call(Long aLong) {
                        return 1L;
                    }
                })
                .scan(new Func2<Long, Long, Long>() {
                    @Override
                    public Long call(Long aLong, Long aLong2) {
                        return aLong + 1;
                    }
                });
        Observable<Long> compose =
                Observable.zip(
                        intervalObservable,
                        counterObservable,
                        new Func2<Long, Long, Long[]>() {
                            @Override
                            public Long[] call(Long interval, Long counter) {
                                return new Long[]{counter, interval};
                            }
                        })
                        .concatMap(new Func1<Long[], Observable<Long>>() {
                            @Override
                            public Observable<Long> call(Long[] longs) {
                                return Observable.just(longs[0])
                                        .delay(longs[1], TimeUnit.MILLISECONDS);
                            }
                        })
                        .compose(new ConcurrentSwitchMapTransformer<>(cacheSIze,
                                new Func1<Long, Observable<Long>>() {
                                    @Override
                                    public Observable<Long> call(Long integer) {
                                        return createLongRunningTask(integer);
                                    }
                                }))
                        .subscribeOn(Schedulers.io());


        TestSubscriber<Long> testSubscriber = new TestSubscriber<>();
        compose.subscribe(testSubscriber);
        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertValues(expectedValues);
    }

    @NonNull
    private Observable<Long> createLongRunningTask(final Long num) {
        return Observable.fromCallable(new Func0<Long>() {
            @Override
            public Long call() {
                log("task #%d start running", num);
                try {
                    Thread.sleep(taskLength);
                } catch (InterruptedException e) {
                    log("task #%d Interrupted", num);
                }
                return num;
            }
        })
                .doOnTerminate(new Action0() {
                    @Override
                    public void call() {
                        log("task #%d doOnTerminate", num);
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        log("task #%d doOnUnsubscribe", num);
                    }
                })
                .subscribeOn(Schedulers.io());
    }

}