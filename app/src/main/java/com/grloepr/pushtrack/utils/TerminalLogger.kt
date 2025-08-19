package com.grloepr.pushtrack.utils

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced logging utility that outputs to both Android Log and system out
 * for real-time monitoring via ADB logcat and Android Studio terminal
 */
object TerminalLogger {
    
    private val dateFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logChannel = Channel<LogEntry>(Channel.UNLIMITED)
    
    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val threadName: String = Thread.currentThread().name
    )
    
    enum class LogLevel(val symbol: String) {
        DEBUG("🔍"),
        INFO("ℹ️"),
        WARNING("⚠️"),
        ERROR("❌"),
        SUCCESS("✅"),
        METRIC("📊"),
        CALIBRATION("🎯")
    }
    
    init {
        logScope.launch {
            logChannel.receiveAsFlow().collect { processLogEntry(it) }
        }
    }
    
    private fun processLogEntry(entry: LogEntry) {
        val ts = dateFormatter.format(Date(entry.timestamp))
        val formatted = "[$ts] ${entry.level.symbol} [${entry.tag}] ${entry.message}" +
                if (entry.threadName != "main") " (${entry.threadName})" else ""
        
        // Output to Android Log (visible in ADB logcat)
        when (entry.level) {
            LogLevel.DEBUG -> Log.d(entry.tag, entry.message)
            LogLevel.INFO, LogLevel.SUCCESS, LogLevel.METRIC, LogLevel.CALIBRATION -> Log.i(entry.tag, entry.message)
            LogLevel.WARNING -> Log.w(entry.tag, entry.message)
            LogLevel.ERROR -> Log.e(entry.tag, entry.message)
        }
        
        // Output to System.out (visible in Android Studio terminal)
        println(formatted)
        
        // Flush to ensure immediate output
        System.out.flush()
    }
    
    private fun log(level: LogLevel, tag: String, message: String) {
        logChannel.trySend(
            LogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = message
            )
        )
    }
    
    // Public logging API
    fun d(tag: String, msg: String) = log(LogLevel.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(LogLevel.INFO, tag, msg)
    fun w(tag: String, msg: String) = log(LogLevel.WARNING, tag, msg)
    fun e(tag: String, msg: String) = log(LogLevel.ERROR, tag, msg)
    fun s(tag: String, msg: String) = log(LogLevel.SUCCESS, tag, msg)
    fun m(tag: String, msg: String) = log(LogLevel.METRIC, tag, msg)
    fun c(tag: String, msg: String) = log(LogLevel.CALIBRATION, tag, msg)
    
    /**
     * Log information in a table format
     */
    fun table(tag: String, title: String, data: Map<String, Any?>) {
        val maxKeyLength = data.keys.maxOfOrNull { it.length } ?: 0
        val formatted = buildString {
            appendLine("📋 $title")
            appendLine("┌${"─".repeat(maxKeyLength + 25)}┐")
            data.forEach { (key, value) ->
                val paddedKey = key.padEnd(maxKeyLength)
                appendLine("│ $paddedKey │ ${value ?: "N/A"}")
            }
            appendLine("└${"─".repeat(maxKeyLength + 25)}┘")
        }
        d(tag, formatted)
    }
    
    /**
     * Log a separator for visual organization
     */
    fun separator(tag: String, title: String = "") {
        val line = "═".repeat(50)
        if (title.isEmpty()) {
            i(tag, line)
        } else {
            i(tag, "$line\n $title \n$line")
        }
    }
    
    /**
     * Log performance metrics
     */
    fun performance(tag: String, op: String, durationMs: Long, details: String = "") {
        m(tag, "⚡ $op completed in ${durationMs}ms${if (details.isNotEmpty()) " | $details" else ""}")
    }
    
    /**
     * Log error with stack trace
     */
    fun error(tag: String, message: String, t: Throwable?) {
        e(tag, "$message${t?.let { "\n${it.stackTraceToString()}" } ?: ""}")
    }
    
    /**
     * Log calibration start
     */
    fun logCalibrationStart(exerciseType: String, sessionId: String) {
        c("Calibration", "📊 Starting calibration for $exerciseType (Session ID: $sessionId)")
    }
    
    /**
     * Log rep completion
     */
    fun logRepCompleted(currentRep: Int, targetReps: Int, metric: Double?) {
        c("Calibration", "✓ Rep $currentRep/$targetReps completed (Metric: $metric)")
    }
    
    /**
     * Log data point
     */
    fun logDataPoint(frameNumber: Int, metric: Double?, state: String, confidence: Float) {
        c("CalibrationData", "Frame $frameNumber: Metric=$metric, State=$state, Confidence=$confidence")
    }
    
    /**
     * Log analysis result
     */
    fun logAnalysisResult(up: Double?, down: Double?, confidence: Float?) {
        table("Analysis", "Results", mapOf(
            "Up Threshold" to (up ?: "N/A"),
            "Down Threshold" to (down ?: "N/A"),
            "Confidence" to (confidence ?: "N/A")
        ))
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        logScope.cancel()
        logChannel.close()
    }
}

/**
 * Extension functions for easy logging from any class
 */
fun Any.logDebug(msg: String) = TerminalLogger.d(this::class.simpleName ?: "Unknown", msg)
fun Any.logInfo(msg: String) = TerminalLogger.i(this::class.simpleName ?: "Unknown", msg)
fun Any.logWarning(msg: String) = TerminalLogger.w(this::class.simpleName ?: "Unknown", msg)
fun Any.logError(msg: String, t: Throwable? = null) = TerminalLogger.error(this::class.simpleName ?: "Unknown", msg, t)
fun Any.logSuccess(msg: String) = TerminalLogger.s(this::class.simpleName ?: "Unknown", msg)
fun Any.logMetric(msg: String) = TerminalLogger.m(this::class.simpleName ?: "Unknown", msg)
fun Any.logCalibration(msg: String) = TerminalLogger.c(this::class.simpleName ?: "Unknown", msg)

/**
 * Measure and log execution time
 */
inline fun <T> measureAndLog(tag: String, operation: String, block: () -> T): T {
    val start = System.currentTimeMillis()
    return try {
        val result = block()
        TerminalLogger.performance(tag, operation, System.currentTimeMillis() - start)
        result
    } catch (e: Exception) {
        TerminalLogger.error(tag, "$operation failed after ${System.currentTimeMillis() - start}ms", e)
        throw e
    }
}
