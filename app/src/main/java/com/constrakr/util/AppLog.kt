package com.constrakr.util

import android.util.Log
import com.constrakr.BuildConfig

/** Logcat tag — filter Android Studio Logcat with: `tag:ConsTrakr` */
object AppLog {
    private const val TAG = "ConsTrakr"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(TAG, message, throwable) else Log.w(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
    }
}
