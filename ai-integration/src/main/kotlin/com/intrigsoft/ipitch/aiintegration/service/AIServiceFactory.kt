package com.intrigsoft.ipitch.aiintegration.service

import com.intrigsoft.ipitch.aiintegration.config.AIProperties
import com.intrigsoft.ipitch.aiintegration.model.AIProvider
import org.springframework.stereotype.Component

/**
 * Factory for creating the appropriate AI service based on configuration
 */
@Component
class AIServiceFactory(
    private val openAIService: OpenAIService,
    private val aiProperties: AIProperties
) {

    /**
     * Get the configured AI service
     */
    fun getAIService(): AIService {
        return when (aiProperties.provider) {
            AIProvider.OPENAI -> openAIService
            AIProvider.ANTHROPIC -> throw NotImplementedError("Anthropic provider not yet implemented. Use OpenAI for now.")
            AIProvider.LOCAL -> throw NotImplementedError("Local AI provider not yet implemented. Use OpenAI for now.")
        }
    }

    /**
     * Get the current provider
     */
    fun getCurrentProvider(): AIProvider = aiProperties.provider
}
