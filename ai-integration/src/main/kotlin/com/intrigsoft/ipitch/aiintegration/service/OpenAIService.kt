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
        val startTime = System.currentTimeMillis()
        logger.info { "[AI] Starting completion request - model: ${config.model}, temperature: $temperature, maxTokens: $maxTokens" }
        logger.debug { "[AI] Prompt length: ${prompt.length} chars, SystemPrompt: ${systemPrompt?.length ?: 0} chars" }

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

        logger.debug { "[AI] Sending completion request to OpenAI API: ${config.baseUrl}/chat/completions" }

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response from OpenAI")
            val duration = System.currentTimeMillis() - startTime

            if (!response.isSuccessful) {
                logger.error { "[AI] OpenAI API error: status=${response.code}, duration=${duration}ms, response=$responseBody" }
                throw Exception("OpenAI API error: ${response.code} - $responseBody")
            }

            val jsonResponse = objectMapper.readTree(responseBody)
            val content = jsonResponse["choices"][0]["message"]["content"].asText()
            val usage = jsonResponse["usage"]

            logger.info { "[AI] Completion successful - duration: ${duration}ms, response length: ${content.length} chars" }
            logger.debug { "[AI] Token usage - prompt: ${usage?.get("prompt_tokens")}, completion: ${usage?.get("completion_tokens")}, total: ${usage?.get("total_tokens")}" }

            content
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error(e) { "[AI] Completion request failed after ${duration}ms: ${e.message}" }
            throw e
        }
    }

    override suspend fun generateEmbedding(text: String): FloatArray = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        logger.info { "[AI] Starting embedding request - model: ${config.embeddingModel}, text length: ${text.length} chars" }

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

        logger.debug { "[AI] Generating embedding with OpenAI API: ${config.baseUrl}/embeddings" }

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response from OpenAI")
            val duration = System.currentTimeMillis() - startTime

            if (!response.isSuccessful) {
                logger.error { "[AI] OpenAI embedding error: status=${response.code}, duration=${duration}ms, response=$responseBody" }
                throw Exception("OpenAI API error: ${response.code} - $responseBody")
            }

            val jsonResponse = objectMapper.readTree(responseBody)
            val embeddingNode = jsonResponse["data"][0]["embedding"]
            val usage = jsonResponse["usage"]
            val embeddingArray = FloatArray(embeddingNode.size()) { i -> embeddingNode[i].floatValue() }

            logger.info { "[AI] Embedding successful - duration: ${duration}ms, dimension: ${embeddingArray.size}" }
            logger.debug { "[AI] Token usage - total: ${usage?.get("total_tokens")}" }

            embeddingArray
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error(e) { "[AI] Embedding request failed after ${duration}ms: ${e.message}" }
            throw e
        }
    }

    override suspend fun generateStructuredResponse(
        prompt: String,
        systemPrompt: String?,
        schema: String?
    ): String = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        logger.info { "[AI] Starting structured response request - model: ${config.model}, schema provided: ${schema != null}" }
        logger.debug { "[AI] Prompt length: ${prompt.length} chars, SystemPrompt: ${systemPrompt?.length ?: 0} chars" }

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

        logger.debug { "[AI] Sending structured response request to OpenAI API: ${config.baseUrl}/chat/completions" }

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response from OpenAI")
            val duration = System.currentTimeMillis() - startTime

            if (!response.isSuccessful) {
                logger.error { "[AI] OpenAI structured response error: status=${response.code}, duration=${duration}ms, response=$responseBody" }
                throw Exception("OpenAI API error: ${response.code} - $responseBody")
            }

            val jsonResponse = objectMapper.readTree(responseBody)
            val content = jsonResponse["choices"][0]["message"]["content"].asText()
            val usage = jsonResponse["usage"]

            logger.info { "[AI] Structured response successful - duration: ${duration}ms, response length: ${content.length} chars" }
            logger.debug { "[AI] Token usage - prompt: ${usage?.get("prompt_tokens")}, completion: ${usage?.get("completion_tokens")}, total: ${usage?.get("total_tokens")}" }

            content
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error(e) { "[AI] Structured response request failed after ${duration}ms: ${e.message}" }
            throw e
        }
    }

    override suspend fun generateEmbeddings(texts: List<String>): List<FloatArray> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        logger.info { "[AI] Starting batch embedding request - model: ${config.embeddingModel}, count: ${texts.size} texts" }
        logger.debug { "[AI] Total text length: ${texts.sumOf { it.length }} chars" }

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

        logger.debug { "[AI] Generating ${texts.size} embeddings with OpenAI API: ${config.baseUrl}/embeddings" }

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response from OpenAI")
            val duration = System.currentTimeMillis() - startTime

            if (!response.isSuccessful) {
                logger.error { "[AI] OpenAI batch embedding error: status=${response.code}, duration=${duration}ms, response=$responseBody" }
                throw Exception("OpenAI API error: ${response.code} - $responseBody")
            }

            val jsonResponse = objectMapper.readTree(responseBody)
            val dataArray = jsonResponse["data"]
            val usage = jsonResponse["usage"]

            val embeddings = (0 until dataArray.size()).map { i ->
                val embeddingNode = dataArray[i]["embedding"]
                FloatArray(embeddingNode.size()) { j -> embeddingNode[j].floatValue() }
            }

            logger.info { "[AI] Batch embedding successful - duration: ${duration}ms, embeddings: ${embeddings.size}, dimension: ${embeddings.firstOrNull()?.size ?: 0}" }
            logger.debug { "[AI] Token usage - total: ${usage?.get("total_tokens")}" }

            embeddings
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error(e) { "[AI] Batch embedding request failed after ${duration}ms: ${e.message}" }
            throw e
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
