package com.intrigsoft.ipitch.proposalmanager.config

import com.intrigsoft.ipitch.config.CorrelationIdFilter
import com.intrigsoft.ipitch.config.CorrelationIdUtil
import feign.Logger
import feign.RequestInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FeignConfig {

    @Bean
    fun feignLoggerLevel(): Logger.Level {
        return Logger.Level.BASIC
    }

    /**
     * Intercepts Feign requests to propagate correlation ID to downstream services.
     */
    @Bean
    fun correlationIdInterceptor(): RequestInterceptor {
        return RequestInterceptor { template ->
            val correlationId = CorrelationIdUtil.getCorrelationId()
            if (correlationId != "NO_CORRELATION_ID") {
                template.header(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId)
            }
        }
    }
}
