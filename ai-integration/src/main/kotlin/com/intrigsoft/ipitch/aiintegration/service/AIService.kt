package com.intrigsoft.ipitch.aiintegration.service

/**
 * Base interface for AI service providers
 * Supports different AI models (OpenAI, Anthropic, Local)
 */
interface AIService {

    /**
     * Generate text completion from a prompt
     */
    suspend fun generateCompletion(
        prompt: String,
        systemPrompt: String? = null,
        temperature: Double = 0.7,
        maxTokens: Int = 2000
    ): String

    /**
     * Generate embeddings for text
     */
    suspend fun generateEmbedding(text: String): FloatArray

    /**
     * Generate structured JSON response
     */
    suspend fun generateStructuredResponse(
        prompt: String,
        systemPrompt: String? = null,
        schema: String? = null
    ): String

    /**
     * Batch generate embeddings for multiple texts
     */
    suspend fun generateEmbeddings(texts: List<String>): List<FloatArray>

    /**
     * Check if the service is available
     */
    suspend fun healthCheck(): Boolean
}
