package com.example.droneservicesapp.common

import android.util.Log
import kotlin.math.pow

/**
 * Utility for retrying operations with exponential backoff
 */
object RetryHelper {

    suspend fun <T> withRetry(
        maxAttempts: Int = 5,
        initialDelayMs: Long = 100,
        backoffMultiplier: Double = 2.0,
        tag: String = "RetryHelper",
        block: suspend () -> T?
    ): T? {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        
        var delayMs = initialDelayMs
        
        repeat(maxAttempts) { attempt ->
            try {
                val result = block()
                if (result != null) {
                    if (attempt > 0) {
                        Log.i(tag, "Success on attempt ${attempt + 1}")
                    }
                    return result
                }
            } catch (e: Exception) {
                if (attempt == maxAttempts - 1) {
                    Log.e(tag, "Failed after $maxAttempts attempts", e)
                    throw e
                }
            }
            
            if (attempt < maxAttempts - 1) {
                Log.d(tag, "Attempt ${attempt + 1} failed, retrying in ${delayMs}ms...")
                kotlinx.coroutines.delay(delayMs)
                delayMs = (delayMs * backoffMultiplier).toLong()
            }
        }
        
        return null
    }

    /**
     * Synchronous version for backward compatibility
     */
    fun <T> withRetrySynchronous(
        maxAttempts: Int = 5,
        tag: String = "RetryHelper",
        block: () -> T?
    ): T? {
        repeat(maxAttempts) { attempt ->
            try {
                val result = block()
                if (result != null) {
                    if (attempt > 0) {
                        Log.i(tag, "Success on attempt ${attempt + 1}")
                    }
                    return result
                }
            } catch (e: Exception) {
                if (attempt == maxAttempts - 1) {
                    Log.e(tag, "Failed after $maxAttempts attempts", e)
                }
            }
        }
        return null
    }
}
