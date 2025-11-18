package com.intrigsoft.ipitch.aiintegration.repository

import com.intrigsoft.ipitch.aiintegration.model.EmbeddingVector
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Repository for vector embeddings with pgvector similarity search
 */
@Repository
interface EmbeddingRepository : JpaRepository<EmbeddingVector, UUID> {

    /**
     * Find embedding by entity type and ID
     */
    fun findByEntityTypeAndEntityId(entityType: String, entityId: UUID): EmbeddingVector?

    /**
     * Find top K similar embeddings using cosine similarity
     * Uses pgvector's <=> operator for cosine distance
     */
    @Query(
        value = """
            SELECT * FROM embeddings
            WHERE entity_type = :entityType
            ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :k
        """,
        nativeQuery = true
    )
    fun findSimilarByEmbedding(
        @Param("queryEmbedding") queryEmbedding: String,
        @Param("entityType") entityType: String,
        @Param("k") k: Int
    ): List<EmbeddingVector>

    /**
     * Find similar embeddings within a similarity threshold
     * Returns only results with distance <= threshold
     */
    @Query(
        value = """
            SELECT e.*, (e.embedding <=> CAST(:queryEmbedding AS vector)) as distance
            FROM embeddings e
            WHERE entity_type = :entityType
            AND (e.embedding <=> CAST(:queryEmbedding AS vector)) <= :threshold
            ORDER BY distance
            LIMIT :k
        """,
        nativeQuery = true
    )
    fun findSimilarWithinThreshold(
        @Param("queryEmbedding") queryEmbedding: String,
        @Param("entityType") entityType: String,
        @Param("threshold") threshold: Double,
        @Param("k") k: Int
    ): List<EmbeddingVector>

    /**
     * Delete embeddings for a specific entity
     */
    fun deleteByEntityTypeAndEntityId(entityType: String, entityId: UUID)
}
