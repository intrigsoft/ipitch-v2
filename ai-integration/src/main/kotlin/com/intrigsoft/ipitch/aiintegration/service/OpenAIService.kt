package com.intrigsoft.ipitch.aiintegration.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intrigsoft.ipitch.aiintegration.config.AIProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.stereotype.Service

/**
 * OpenAI API implementation
 */
@Service
class OpenAIService(
    private val httpClient: OkHttpClient,
    private val aiProperties: AIProperties,
    private val objectMapper: ObjectMapper
) : AIService {

    private val logger = KotlinLogging.logger {}
    private val config = aiProperties.openai
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun generateCompletion(
        prompt: String,
        systemPrompt: String?,
        temperature: Double,
        maxTokens: Int
    ): String = withContext(Dispatchers.IO) {
        val messages = mutableListOf<Map<String, String>>()

        if (systemPrompt != null) {
            messages.add(mapOf("role" to "system", "content" to systemPrompt))
        }
        messages.add(mapOf("role" to "user", "content" to prompt))

        val requestBody = mapOf(
            "model" to config.model,
            "messages" to messages,
            "temperature" to temperature,
            "max_tokens" to maxTokens
        )

        val request = Request.Builder()
            .url("${config.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(objectMapper.writeValueAsString(requestBody).toRequestBody(jsonMediaType))
            .build()

        logger.debug { "Sending completion request to OpenAI" }

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response from OpenAI")

        if (!response.isSuccessful) {
            logger.error { "OpenAI API error: ${response.code} - $responseBody" }
            throw Exception("OpenAI API error: ${response.code} - $responseBody")
        }

        val jsonResponse = objectMapper.readTree(responseBody)
        jsonResponse["choices"][0]["message"]["content"].asText()
    }

    override suspend fun generateEmbedding(text: String): FloatArray = withContext(Dispatchers.IO) {
        val requestBody = mapOf(
            "model" to config.embeddingModel,
            "input" to text,
            "encoding_format" to "float"
        )

        val request = Request.Builder()
            .url("${config.baseUrl}/embeddings")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(objectMapper.writeValueAsString(requestBody).toRequestBody(jsonMediaType))
            .build()

        logger.debug { "Generating embedding with OpenAI" }

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response from OpenAI")

        if (!response.isSuccessful) {
            logger.error { "OpenAI API error: ${response.code} - $responseBody" }
            throw Exception("OpenAI API error: ${response.code} - $responseBody")
        }

        val jsonResponse = objectMapper.readTree(responseBody)
        val embeddingNode = jsonResponse["data"][0]["embedding"]

        FloatArray(embeddingNode.size()) { i -> embeddingNode[i].floatValue() }
    }

    override suspend fun generateStructuredResponse(
        prompt: String,
        systemPrompt: String?,
        schema: String?
    ): String = withContext(Dispatchers.IO) {
        val messages = mutableListOf<Map<String, String>>()

        val enhancedSystemPrompt = if (schema != null) {
            "${systemPrompt ?: ""}\n\nRespond with valid JSON matching this schema:\n$schema"
        } else {
            "${systemPrompt ?: ""}\n\nRespond with valid JSON only."
        }

        messages.add(mapOf("role" to "system", "content" to enhancedSystemPrompt))
        messages.add(mapOf("role" to "user", "content" to prompt))

        val requestBody = mutableMapOf<String, Any>(
            "model" to config.model,
            "messages" to messages,
            "temperature" to 0.3,
            "response_format" to mapOf("type" to "json_object")
        )

        val request = Request.Builder()
            .url("${config.baseUrl}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(objectMapper.writeValueAsString(requestBody).toRequestBody(jsonMediaType))
            .build()

        logger.debug { "Sending structured response request to OpenAI" }

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response from OpenAI")

        if (!response.isSuccessful) {
            logger.error { "OpenAI API error: ${response.code} - $responseBody" }
            throw Exception("OpenAI API error: ${response.code} - $responseBody")
        }

        val jsonResponse = objectMapper.readTree(responseBody)
        jsonResponse["choices"][0]["message"]["content"].asText()
    }

    override suspend fun generateEmbeddings(texts: List<String>): List<FloatArray> = withContext(Dispatchers.IO) {
        val requestBody = mapOf(
            "model" to config.embeddingModel,
            "input" to texts,
            "encoding_format" to "float"
        )

        val request = Request.Builder()
            .url("${config.baseUrl}/embeddings")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(objectMapper.writeValueAsString(requestBody).toRequestBody(jsonMediaType))
            .build()

        logger.debug { "Generating ${texts.size} embeddings with OpenAI" }

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response from OpenAI")

        if (!response.isSuccessful) {
            logger.error { "OpenAI API error: ${response.code} - $responseBody" }
            throw Exception("OpenAI API error: ${response.code} - $responseBody")
        }

        val jsonResponse = objectMapper.readTree(responseBody)
        val dataArray = jsonResponse["data"]

        (0 until dataArray.size()).map { i ->
            val embeddingNode = dataArray[i]["embedding"]
            FloatArray(embeddingNode.size()) { j -> embeddingNode[j].floatValue() }
        }
    }

    override suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${config.baseUrl}/models")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            logger.error(e) { "OpenAI health check failed" }
            false
        }
    }
}
