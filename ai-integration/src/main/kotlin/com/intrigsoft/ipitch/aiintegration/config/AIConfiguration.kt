package com.intrigsoft.ipitch.aiintegration.config

import okhttp3.OkHttpClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

/**
 * Configuration for AI integration module
 */
@Configuration
@EnableConfigurationProperties(AIProperties::class, VectorDatabaseProperties::class)
class AIConfiguration {

    /**
     * HTTP client for AI API calls
     */
    @Bean
    fun aiHttpClient(aiProperties: AIProperties): OkHttpClient {
        val timeout = when (aiProperties.provider) {
            com.intrigsoft.ipitch.aiintegration.model.AIProvider.OPENAI -> aiProperties.openai.timeout
            com.intrigsoft.ipitch.aiintegration.model.AIProvider.ANTHROPIC -> aiProperties.anthropic.timeout
            com.intrigsoft.ipitch.aiintegration.model.AIProvider.LOCAL -> aiProperties.local.timeout
        }

        return OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.MILLISECONDS)
            .readTimeout(timeout, TimeUnit.MILLISECONDS)
            .writeTimeout(timeout, TimeUnit.MILLISECONDS)
            .build()
    }
}
