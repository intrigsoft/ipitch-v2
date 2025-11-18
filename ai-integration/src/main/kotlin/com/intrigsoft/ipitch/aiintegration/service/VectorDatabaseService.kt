package com.intrigsoft.ipitch.aiintegration.service

import com.intrigsoft.ipitch.aiintegration.config.AIProperties
import com.intrigsoft.ipitch.aiintegration.config.VectorDatabaseProperties
import com.intrigsoft.ipitch.aiintegration.model.EmbeddingVector
import com.intrigsoft.ipitch.aiintegration.repository.EmbeddingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Service for managing vector embeddings and similarity search
 */
@Service
class VectorDatabaseService(
    private val embeddingRepository: EmbeddingRepository,
    private val aiServiceFactory: AIServiceFactory,
    private val vectorDbProperties: VectorDatabaseProperties,
    private val aiProperties: AIProperties
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Store embedding for an entity
     */
    @Transactional
    suspend fun storeEmbedding(
        entityType: String,
        entityId: UUID,
        text: String,
        model: String
    ): EmbeddingVector = withContext(Dispatchers.IO) {
        logger.info { "Generating and storing embedding for $entityType:$entityId" }

        // Delete existing embedding if any
        embeddingRepository.findByEntityTypeAndEntityId(entityType, entityId)?.let {
            embeddingRepository.delete(it)
            logger.debug { "Deleted existing embedding for $entityType:$entityId" }
        }

        // Generate new embedding
        val aiService = aiServiceFactory.getAIService()
        val embedding = aiService.generateEmbedding(text)

        // Store embedding
        val embeddingVector = EmbeddingVector(
            entityType = entityType,
            entityId = entityId,
            embedding = embedding,
            model = model,
            dimension = embedding.size,
            createdAt = Instant.now()
        )

        embeddingRepository.save(embeddingVector)
        logger.info { "Successfully stored embedding for $entityType:$entityId" }

        embeddingVector
    }

    /**
     * Find similar entities using RAG
     */
    @Transactional(readOnly = true)
    suspend fun findSimilar(
        queryText: String,
        entityType: String,
        topK: Int = aiProperties.rag.topK,
        similarityThreshold: Double = aiProperties.rag.similarityThreshold
    ): List<Pair<EmbeddingVector, Double>> = withContext(Dispatchers.IO) {
        logger.debug { "Finding similar entities for type: $entityType, topK: $topK" }

        // Generate query embedding
        val aiService = aiServiceFactory.getAIService()
        val queryEmbedding = aiService.generateEmbedding(queryText)

        // Convert to pgvector format
        val embeddingString = "[${queryEmbedding.joinToString(",")}]"

        // Find similar embeddings
        val results = embeddingRepository.findSimilarByEmbedding(
            queryEmbedding = embeddingString,
            entityType = entityType,
            k = topK
        )

        // Calculate cosine similarity for each result
        results.map { embedding ->
            val similarity = cosineSimilarity(queryEmbedding, embedding.embedding)
            embedding to similarity
        }.filter { it.second >= similarityThreshold }
            .sortedByDescending { it.second }
    }

    /**
     * Get embedding for an entity
     */
    @Transactional(readOnly = true)
    fun getEmbedding(entityType: String, entityId: UUID): EmbeddingVector? {
        return embeddingRepository.findByEntityTypeAndEntityId(entityType, entityId)
    }

    /**
     * Delete embedding for an entity
     */
    @Transactional
    fun deleteEmbedding(entityType: String, entityId: UUID) {
        embeddingRepository.deleteByEntityTypeAndEntityId(entityType, entityId)
        logger.info { "Deleted embedding for $entityType:$entityId" }
    }

    /**
     * Calculate cosine similarity between two vectors
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        require(a.size == b.size) { "Vectors must have same dimension" }

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        return if (normA == 0.0 || normB == 0.0) {
            0.0
        } else {
            dotProduct / (Math.sqrt(normA) * Math.sqrt(normB))
        }
    }

    /**
     * Check if embedding exists for an entity
     */
    fun hasEmbedding(entityType: String, entityId: UUID): Boolean {
        return embeddingRepository.findByEntityTypeAndEntityId(entityType, entityId) != null
    }
}
