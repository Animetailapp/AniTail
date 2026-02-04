package com.anitail.music.ui.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.anitail.music.BuildConfig
import com.anitail.music.ui.screens.CrashActivity
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class CrashHandler private constructor(
    private val applicationContext: Context
) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashLog = buildCrashLog(throwable)
            Timber.Forest.e(throwable, "App crashed")

            // Launch crash activity in separate process
            val intent = Intent(applicationContext, CrashActivity::class.java).apply {
                putExtra(EXTRA_CRASH_LOG, crashLog)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            applicationContext.startActivity(intent)

            // Give the crash activity time to start before killing this process
            Thread.sleep(500)

            // Kill the current process
            Process.killProcess(Process.myPid())
            exitProcess(1)
        } catch (e: Exception) {
            // If we fail to handle the crash, fall back to default handler
            Timber.Forest.e(e, "Error handling crash")
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildCrashLog(throwable: Throwable): String {
        val stackTrace = StringWriter().apply {
            throwable.printStackTrace(PrintWriter(this))
        }.toString()

        return buildString {
            appendLine("anitail Crash Report")
            appendLine("=".repeat(50))
            appendLine()
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Device: ${Build.MODEL}")
            appendLine("Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine()
            appendLine("=".repeat(50))
            appendLine("Stacktrace:")
            appendLine("=".repeat(50))
            appendLine()
            append(stackTrace)
        }
    }

    companion object {
        const val EXTRA_CRASH_LOG = "crash_log"
        private const val CRASH_PROCESS_SUFFIX = ":crash"

        fun install(context: Context) {
            // Don't install crash handler in the :crash process to avoid infinite loops
            val processName = getProcessName(context)
            if (processName?.endsWith(CRASH_PROCESS_SUFFIX) == true) {
                Timber.Forest.d("CrashHandler not installed in crash process")
                return
            }

            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            Timber.Forest.d("CrashHandler installed in process: $processName")
        }

        private fun getProcessName(context: Context): String? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return android.app.Application.getProcessName()
            }

            val pid = Process.myPid()
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            return activityManager?.runningAppProcesses?.find { it.pid == pid }?.processName
        }
    }
}