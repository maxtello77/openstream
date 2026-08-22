package com.openstream.app

import android.content.Context
import android.os.Process
import kotlin.system.exitProcess

/**
 * Captures any uncaught crash and persists the stack trace, so the next launch
 * can show it on screen (the user has no adb/logcat available).
 */
object CrashReporter {
    private const val PREFS = "openstream"
    private const val KEY = "last_crash"

    fun install(context: Context) {
        val app = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            runCatching {
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY, "${e.javaClass.name}: ${e.message}\n${e.stackTraceToString()}")
                    .commit()
            }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    fun lastCrash(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
