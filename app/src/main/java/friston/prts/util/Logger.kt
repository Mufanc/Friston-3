package friston.prts.util

import android.util.Log

object Logger {
    private const val TAG = "Friston-3"

    fun v(tag: String, msg: String) {
        Log.v(TAG, "[$tag] $msg")
    }

    fun d(tag: String, msg: String) {
        Log.d(TAG, "[$tag] $msg")
    }

    fun i(tag: String, msg: String) {
        Log.i(TAG, "[$tag] $msg")
    }

    fun w(tag: String, msg: String) {
        Log.w(TAG, "[$tag] $msg")
    }

    fun w(tag: String, msg: String, err: Throwable) {
        Log.w(TAG, "[$tag] $msg", err)
    }

    fun e(tag: String, msg: String) {
        Log.e(TAG, "[$tag] $msg")
    }

    fun e(tag: String, msg: String, err: Throwable) {
        Log.e(TAG, "[$tag] $msg", err)
    }

    fun f(tag: String, msg: String) {
        Log.wtf(TAG, "[$tag] $msg")
    }
}
