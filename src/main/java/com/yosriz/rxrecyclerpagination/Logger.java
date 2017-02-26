package com.yosriz.rxrecyclerpagination;


import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Logger {

    private static final int MAX_TAG_LENGTH = 23;
    private static final int CALL_STACK_INDEX = 2;
    private static final Pattern ANONYMOUS_CLASS = Pattern.compile("(\\$\\d+)+$");
    private static final boolean shouldLog = BuildConfig.DEBUG || Log.isLoggable("rxrecyclerpagination", Log.DEBUG);

    public static void d(String format, Object... args) {
        if (shouldLog) {
            Log.d(createTag(), String.format(format, args));
        }
    }

    public static void d(String msg) {
        if (shouldLog) {
            Log.d(createTag(), msg);
        }
    }

    public static void e(Throwable ex, String format, Object... args) {
        if (shouldLog) {
            Log.e(createTag(), String.format(format, args), ex);
        }
    }

    public static void e(Throwable ex, String msg) {
        if (shouldLog) {
            Log.e(createTag(), msg, ex);
        }
    }

    public static void i(String format, Object... args) {
        if (shouldLog) {
            Log.i(createTag(), String.format(format, args));
        }
    }

    public static void i(String msg) {
        if (shouldLog) {
            Log.i(createTag(), msg);
        }
    }

    public static void w(String format, Object... args) {
        if (shouldLog) {
            Log.w(createTag(), String.format(format, args));
        }
    }

    public static void w(String msg) {
        if (shouldLog) {
            Log.w(createTag(), msg);
        }
    }

    public static void wtf(String format, Object... args) {
        Log.wtf(createTag(), String.format(format, args));
    }

    public static void wtf(Throwable ex, String format, Object... args) {
        Log.wtf(createTag(), String.format(format, args), ex);
    }

    private static String createTag() {
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        if (stackTrace.length <= CALL_STACK_INDEX) {
            throw new IllegalStateException(
                    "Synthetic stacktrace didn't have enough elements: are you using proguard?");
        }
        return createStackElementTag(stackTrace[CALL_STACK_INDEX]);
    }

    private static String createStackElementTag(StackTraceElement element) {
        String tag = element.getClassName();
        Matcher m = ANONYMOUS_CLASS.matcher(tag);
        if (m.find()) {
            tag = m.replaceAll("");
        }
        tag = tag.substring(tag.lastIndexOf('.') + 1);
        return tag.length() > MAX_TAG_LENGTH ? tag.substring(0, MAX_TAG_LENGTH) : tag;
    }

}
