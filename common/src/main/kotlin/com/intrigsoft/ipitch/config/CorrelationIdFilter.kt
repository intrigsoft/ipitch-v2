package com.intrigsoft.ipitch.config

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.util.*

/**
 * Filter to handle correlation ID for request tracking across services.
 *
 * This filter:
 * - Extracts correlation ID from X-Correlation-ID header if present
 * - Generates a new UUID if not present
 * - Stores it in MDC (Mapped Diagnostic Context) for logging
 * - Adds it to the response header
 * - Cleans up MDC after request completion
 */
@Component
@Order(1)
class CorrelationIdFilter : Filter {

    companion object {
        const val CORRELATION_ID_HEADER = "X-Correlation-ID"
        const val CORRELATION_ID_MDC_KEY = "correlationId"
    }

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse

        try {
            // Get correlation ID from header or generate new one
            val correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER)
                ?: UUID.randomUUID().toString()

            // Store in MDC for logging
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId)

            // Add to response header for client reference
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId)

            // Continue the filter chain
            chain.doFilter(request, response)
        } finally {
            // Always clean up MDC to prevent memory leaks
            MDC.remove(CORRELATION_ID_MDC_KEY)
        }
    }
}
