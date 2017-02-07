package com.yosriz.rxrecyclerpagination;


import android.annotation.SuppressLint;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Helper class for logging in UnitTest (RxJava is tough...)
 */
public class TestLogger {
    private static long lastLog = System.currentTimeMillis();
    private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-mm-dd HH:mm:ss.SSS", Locale.US);

    public static void log(String msg, Object... args) {
        log(String.format(msg, args));
    }

    public static void log(String msg) {
        long currentTimeMillis = System.currentTimeMillis();
        long delta = currentTimeMillis - lastLog;
        lastLog = currentTimeMillis;
        System.out.print(String.format(Locale.US, "%s +%d : %s\n", simpleDateFormat.format(new Date(currentTimeMillis)), delta, msg));
    }
}
