package com.github.catvod.crawler;

import android.util.Log;

/**
 * Spider 调试日志工具
 *
 * 迁移自 TVBoxOS com.github.catvod.crawler.SpiderDebug
 */
public class SpiderDebug {

    private static final String TAG = "SpiderDebug";

    public static void log(String msg) {
        Log.d(TAG, msg);
    }

    public static void log(Throwable e) {
        Log.e(TAG, Log.getStackTraceString(e));
    }

    public static void print(Throwable th) {
        Log.e(TAG, Log.getStackTraceString(th));
    }
}
