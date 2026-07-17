package com.bychat.app.utils

import android.util.Log

object BLog {
    private const val PREFIX = "Bychat"
    @Volatile var enabled: Boolean = true
    @Volatile var verbose: Boolean = false

    fun d(tag: String, message: String) {
        if (enabled && verbose) Log.d(formatTag(tag), message)
    }

    fun i(tag: String, message: String) {
        if (enabled && verbose) Log.i(formatTag(tag), message)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        if (enabled) Log.w(formatTag(tag), message, error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        if (enabled) Log.e(formatTag(tag), message, error)
    }

    private fun formatTag(tag: String): String = "$PREFIX-${tag.take(15)}"
}
