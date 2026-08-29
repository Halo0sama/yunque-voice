package com.halo.yunquevoice

import android.app.Application
import android.util.Log
import java.io.FileWriter
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class YunqueApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = FileWriter(cacheDir.resolve("crash.log").absolutePath, true)
                file.write("\n==== ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())} ====\n")
                file.write("Thread: ${thread.name}\n")
                file.write("Device: ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                throwable.stackTraceToString().also { file.write(it) }
                file.flush()
                file.close()
            } catch (_: Throwable) {
            }
            Log.e("YunqueCrash", "uncaught exception", throwable)
            default?.uncaughtException(thread, throwable)
        }
    }
}
