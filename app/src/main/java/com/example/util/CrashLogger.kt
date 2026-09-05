package com.example.util

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CrashLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: String, // "FATAL_CRASH", "ERROR", "WARNING", "INFO"
    val tag: String,
    val message: String,
    val stackTrace: String? = null,
    val threadName: String? = null,
    val deviceInfo: String? = null
) {
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}

object CrashLogger {
    private const val TAG = "CrashLogger"
    private const val LOGS_FILE_NAME = "crash_logs.json"
    private const val MAX_LOGS = 50

    private var appContext: Context? = null
    private val _logsFlow = MutableStateFlow<List<CrashLogEntry>>(emptyList())
    val logsFlow: StateFlow<List<CrashLogEntry>> = _logsFlow.asStateFlow()

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadLogsFromFile()

        // Capture previous default uncaught exception handler
        if (defaultHandler == null) {
            defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    recordFatalCrash(thread, throwable)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to record fatal crash", e)
                } finally {
                    // Forward to default Android handler so OS handles crash cleanly
                    defaultHandler?.uncaughtException(thread, throwable)
                }
            }
        }
    }

    private fun getDeviceDetails(): String {
        return buildString {
            append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
            append(" (").append(Build.DEVICE).append(")\n")
            append("Android OS: ").append(Build.VERSION.RELEASE)
            append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            val runtime = Runtime.getRuntime()
            val maxMem = runtime.maxMemory() / (1024 * 1024)
            val freeMem = runtime.freeMemory() / (1024 * 1024)
            val totalMem = runtime.totalMemory() / (1024 * 1024)
            append("Memory: Used ").append(totalMem - freeMem).append("MB / Max ").append(maxMem).append("MB")
        }
    }

    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        pw.flush()
        return sw.toString()
    }

    @Synchronized
    fun recordFatalCrash(thread: Thread, throwable: Throwable) {
        val entry = CrashLogEntry(
            level = "FATAL_CRASH",
            tag = "UncaughtException",
            message = throwable.localizedMessage ?: throwable.javaClass.name,
            stackTrace = getStackTraceString(throwable),
            threadName = thread.name,
            deviceInfo = getDeviceDetails()
        )
        addAndPersistEntry(entry)
    }

    @Synchronized
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val stackTrace = throwable?.let { getStackTraceString(it) }
        val entry = CrashLogEntry(
            level = "ERROR",
            tag = tag,
            message = message,
            stackTrace = stackTrace,
            threadName = Thread.currentThread().name,
            deviceInfo = getDeviceDetails()
        )
        addAndPersistEntry(entry)
    }

    @Synchronized
    fun logWarning(tag: String, message: String, throwable: Throwable? = null) {
        val stackTrace = throwable?.let { getStackTraceString(it) }
        val entry = CrashLogEntry(
            level = "WARNING",
            tag = tag,
            message = message,
            stackTrace = stackTrace,
            threadName = Thread.currentThread().name,
            deviceInfo = getDeviceDetails()
        )
        addAndPersistEntry(entry)
    }

    @Synchronized
    fun logInfo(tag: String, message: String) {
        val entry = CrashLogEntry(
            level = "INFO",
            tag = tag,
            message = message,
            threadName = Thread.currentThread().name
        )
        addAndPersistEntry(entry)
    }

    @Synchronized
    private fun addAndPersistEntry(entry: CrashLogEntry) {
        val current = _logsFlow.value.toMutableList()
        current.add(0, entry) // Newest first
        if (current.size > MAX_LOGS) {
            current.subList(MAX_LOGS, current.size).clear()
        }
        _logsFlow.value = current
        saveLogsToFile(current)
    }

    @Synchronized
    fun clearLogs() {
        _logsFlow.value = emptyList()
        val context = appContext ?: return
        try {
            val file = File(context.filesDir, LOGS_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing logs file", e)
        }
    }

    private fun saveLogsToFile(logs: List<CrashLogEntry>) {
        val context = appContext ?: return
        try {
            val jsonArray = JSONArray()
            logs.forEach { log ->
                val obj = JSONObject().apply {
                    put("id", log.id)
                    put("timestamp", log.timestamp)
                    put("level", log.level)
                    put("tag", log.tag)
                    put("message", log.message)
                    put("stackTrace", log.stackTrace ?: "")
                    put("threadName", log.threadName ?: "")
                    put("deviceInfo", log.deviceInfo ?: "")
                }
                jsonArray.put(obj)
            }
            val file = File(context.filesDir, LOGS_FILE_NAME)
            file.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving logs to file", e)
        }
    }

    private fun loadLogsFromFile() {
        val context = appContext ?: return
        try {
            val file = File(context.filesDir, LOGS_FILE_NAME)
            if (!file.exists()) return
            val text = file.readText()
            if (text.isBlank()) return

            val jsonArray = JSONArray(text)
            val list = mutableListOf<CrashLogEntry>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val entry = CrashLogEntry(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    level = obj.optString("level", "ERROR"),
                    tag = obj.optString("tag", "Unknown"),
                    message = obj.optString("message", ""),
                    stackTrace = obj.optString("stackTrace", "").takeIf { it.isNotBlank() },
                    threadName = obj.optString("threadName", "").takeIf { it.isNotBlank() },
                    deviceInfo = obj.optString("deviceInfo", "").takeIf { it.isNotBlank() }
                )
                list.add(entry)
            }
            _logsFlow.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Error reading logs from file", e)
        }
    }

    fun generateFullReport(): String {
        val logs = _logsFlow.value
        if (logs.isEmpty()) {
            return "No crash or error logs recorded."
        }

        return buildString {
            append("=== KRIZRP SYSTEM DIAGNOSTIC & CRASH REPORT ===\n")
            append("Generated: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())).append("\n")
            append("Total Logged Events: ").append(logs.size).append("\n\n")

            logs.forEachIndexed { index, entry ->
                append("--------------------------------------------------\n")
                append("#").append(index + 1).append(" [").append(entry.level).append("] ")
                append(entry.formattedDate).append("\n")
                append("Tag: ").append(entry.tag).append("\n")
                append("Message: ").append(entry.message).append("\n")
                if (!entry.threadName.isNullOrBlank()) {
                    append("Thread: ").append(entry.threadName).append("\n")
                }
                if (!entry.deviceInfo.isNullOrBlank()) {
                    append("Device Info:\n").append(entry.deviceInfo).append("\n")
                }
                if (!entry.stackTrace.isNullOrBlank()) {
                    append("Stack Trace:\n").append(entry.stackTrace).append("\n")
                }
                append("\n")
            }
            append("================ END OF REPORT ================\n")
        }
    }
}
