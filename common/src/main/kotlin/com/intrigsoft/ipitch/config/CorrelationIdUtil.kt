package com.intrigsoft.ipitch.config

import org.slf4j.MDC

/**
 * Utility object for accessing correlation ID in various contexts.
 */
object CorrelationIdUtil {

    /**
     * Gets the current correlation ID from MDC.
     * @return The correlation ID or "NO_CORRELATION_ID" if not set
     */
    fun getCorrelationId(): String {
        return MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY) ?: "NO_CORRELATION_ID"
    }

    /**
     * Sets the correlation ID in MDC.
     * Useful for async operations or when starting new threads.
     */
    fun setCorrelationId(correlationId: String) {
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, correlationId)
    }

    /**
     * Clears the correlation ID from MDC.
     * Should be called in finally blocks for async operations.
     */
    fun clearCorrelationId() {
        MDC.remove(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)
    }

    /**
     * Executes a block of code with the given correlation ID set in MDC.
     * Automatically cleans up after execution.
     */
    inline fun <T> withCorrelationId(correlationId: String, block: () -> T): T {
        return try {
            setCorrelationId(correlationId)
            block()
        } finally {
            clearCorrelationId()
        }
    }
}
