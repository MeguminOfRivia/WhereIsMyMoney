package com.friday.wimm.util

import android.util.Log
import com.friday.wimm.BuildConfig

/**
 * 日志门面。
 *
 * 记账应用经手的每一条通知都可能包含商户名和金额，属于隐私数据：
 *  - release 包**完全静默**，不会向系统日志写入任何内容；
 *  - debug 包保留结构化日志，方便排查问题，但调用方不得传入通知原文、金额或商户名。
 */
object AppLog {

    private val enabled: Boolean = BuildConfig.DEBUG

    fun d(tag: String, message: () -> String) {
        if (enabled) Log.d(tag, message())
    }

    fun w(tag: String, message: () -> String) {
        if (enabled) Log.w(tag, message())
    }

    fun e(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (!enabled) return
        if (throwable != null) Log.e(tag, message(), throwable) else Log.e(tag, message())
    }
}
