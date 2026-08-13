package com.danycli.gitstreakwidget

import android.app.Application
import android.util.Log
import timber.log.Timber

class GitStreakApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Plant standard DebugTree for local logs
        Timber.plant(Timber.DebugTree())

        // Plant a custom CrashReportingTree for future Crashlytics integration
        Timber.plant(CrashReportingTree())
    }

    private class CrashReportingTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == Log.VERBOSE || priority == Log.DEBUG || priority == Log.INFO) {
                return
            }

            // In the future, send these logs to Firebase Crashlytics
            // FirebaseCrashlytics.getInstance().log(message)

            if (t != null) {
                if (priority == Log.ERROR) {
                    // FirebaseCrashlytics.getInstance().recordException(t)
                } else if (priority == Log.WARN) {
                    // FirebaseCrashlytics.getInstance().recordException(t)
                }
            }
        }
    }
}
