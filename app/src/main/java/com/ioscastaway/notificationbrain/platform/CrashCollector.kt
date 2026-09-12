package com.ioscastaway.notificationbrain.platform

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.util.Log
import com.ioscastaway.notificationbrain.BuildConfig
import com.ioscastaway.notificationbrain.data.BrainRepository
import com.ioscastaway.notificationbrain.data.CrashRecord
import java.io.File

/**
 * Collects this app's own crashes and ANRs. Two sources:
 *  - an uncaught-exception handler, which writes a file synchronously (the database is not a safe
 *    place to be while the process is dying) and then lets the previous handler kill the process;
 *  - `ApplicationExitInfo`, which the system keeps for us and which also covers ANRs and native
 *    crashes our handler never sees.
 * Nothing acts on these in stage 1. They are the input of stage 3.
 */
class CrashCollector(private val context: Context) {
    private val dir = File(context.filesDir, "crashes").apply { mkdirs() }

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val ts = System.currentTimeMillis()
                File(dir, "$ts.txt").writeText(
                    "thread=${thread.name}\n" +
                        "description=${throwable::class.java.name}: ${throwable.message}\n" +
                        "trace=\n" + throwable.stackTraceToString(),
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Call once on startup, off the main thread. */
    suspend fun importPending(repo: BrainRepository) {
        importFiles(repo)
        importExitInfo(repo)
    }

    private suspend fun importFiles(repo: BrainRepository) {
        dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            runCatching {
                val ts = f.nameWithoutExtension.toLongOrNull() ?: f.lastModified()
                val text = f.readText()
                val description = text.lineSequence().firstOrNull { it.startsWith("description=") }
                    ?.removePrefix("description=") ?: "uncaught exception"
                val trace = text.substringAfter("trace=\n", "")
                repo.insertCrash(CrashRecord(
                    timestamp = ts, source = "uncaught", reason = "CRASH",
                    description = description, trace = trace,
                    versionName = BuildConfig.VERSION_NAME, gitSha = BuildConfig.GIT_SHA,
                ))
            }.onFailure { Log.w(TAG, "could not import ${f.name}", it) }
            f.delete()
        }
    }

    private suspend fun importExitInfo(repo: BrainRepository) {
        val am = context.getSystemService(ActivityManager::class.java)
        val newest = repo.newestExitInfo() ?: 0L
        val exits = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 20) }
            .getOrElse { Log.w(TAG, "exit reasons unavailable", it); return }
        exits.filter { it.timestamp > newest && it.reason in interestingReasons }
            .sortedBy { it.timestamp }
            .forEach { info ->
                val trace = runCatching {
                    info.traceInputStream?.bufferedReader()?.use { r -> r.readText().take(64 * 1024) }
                }.getOrNull() ?: ""
                repo.insertCrash(CrashRecord(
                    timestamp = info.timestamp, source = "exit-info",
                    reason = reasonName(info.reason), description = info.description ?: "",
                    trace = trace, versionName = BuildConfig.VERSION_NAME, gitSha = BuildConfig.GIT_SHA,
                ))
            }
    }

    private val interestingReasons = setOf(
        ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR,
    )

    private fun reasonName(reason: Int) = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        else -> "REASON_$reason"
    }

    private companion object { const val TAG = "CrashCollector" }
}
