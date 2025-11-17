package com.intrigsoft.ipitch.aiintegration.model

/**
 * Supported AI providers for the platform
 */
enum class AIProvider {
    OPENAI,
    ANTHROPIC,
    LOCAL  // For self-hosted models like Ollama
}
