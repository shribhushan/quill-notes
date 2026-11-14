package com.quillnotes

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash reporting that keeps Quill's promise of no tracking: nothing is sent anywhere. An
 * uncaught crash writes a short report (versions, device, stack frames) to a private file; the
 * next time the app opens it offers to share it, and the user chooses whether and where.
 *
 * Exception messages are left out on purpose. They can quote data (a SQL value, a note's
 * title), and a crash report must never carry anyone's notes.
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                File(app.filesDir, FILE).writeText(report(thread.name, throwable))
            } catch (ignored: Throwable) {
                // Nothing sensible to do if even that fails.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** The report from the last crash, if there is one nobody has dealt with yet. */
    fun pending(context: Context): String? =
        File(context.filesDir, FILE).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    internal fun report(threadName: String, throwable: Throwable): String = buildString {
        appendLine("Quill Notes ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
        appendLine("Thread: $threadName")
        appendLine()
        var t: Throwable? = throwable
        var depth = 0
        while (t != null && depth < 6) {
            appendLine((if (depth == 0) "" else "Caused by: ") + t.javaClass.name)
            t.stackTrace.take(40).forEach { appendLine("    at $it") }
            t = t.cause
            depth++
        }
    }
}
