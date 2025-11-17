package com.intrigsoft.ipitch.aiintegration.config

import com.intrigsoft.ipitch.aiintegration.model.AIProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Configuration properties for AI services
 */
@Configuration
@ConfigurationProperties(prefix = "ai")
data class AIProperties(
    var provider: AIProvider = AIProvider.OPENAI,
    var openai: OpenAIConfig = OpenAIConfig(),
    var anthropic: AnthropicConfig = AnthropicConfig(),
    var local: LocalAIConfig = LocalAIConfig(),
    var rag: RAGConfig = RAGConfig(),
    var sectors: SectorConfig = SectorConfig(),
    var moderation: ModerationConfig = ModerationConfig()
)

data class OpenAIConfig(
    var apiKey: String = "",
    var model: String = "gpt-4-turbo-preview",
    var embeddingModel: String = "text-embedding-3-large",
    var baseUrl: String = "https://api.openai.com/v1",
    var timeout: Long = 60000,
    var maxRetries: Int = 3
)

data class AnthropicConfig(
    var apiKey: String = "",
    var model: String = "claude-3-5-sonnet-20241022",
    var baseUrl: String = "https://api.anthropic.com/v1",
    var timeout: Long = 60000,
    var maxRetries: Int = 3
)

data class LocalAIConfig(
    var baseUrl: String = "http://localhost:11434",
    var model: String = "llama2",
    var embeddingModel: String = "nomic-embed-text",
    var timeout: Long = 120000
)

data class RAGConfig(
    var topK: Int = 5,
    var similarityThreshold: Double = 0.7,
    var chunkSize: Int = 1000,
    var chunkOverlap: Int = 200
)

data class SectorConfig(
    var enabled: List<String> = listOf(
        "IT", "Legal", "Media", "Environment", "Healthcare",
        "Education", "Transport", "Tourism", "Finance",
        "Agriculture", "Energy", "Manufacturing"
    )
)

data class ModerationConfig(
    var enabled: Boolean = true,
    var autoFlagThreshold: Double = 0.8,
    var categories: List<String> = listOf(
        "hate", "harassment", "self-harm", "sexual",
        "violence", "profanity", "spam"
    )
)
