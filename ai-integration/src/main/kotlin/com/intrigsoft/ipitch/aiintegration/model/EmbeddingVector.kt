package com.intrigsoft.ipitch.aiintegration.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Represents a vector embedding stored in the database
 * Uses pgvector extension for efficient similarity search
 */
@Entity
@Table(name = "embeddings")
data class EmbeddingVector(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val entityType: String,  // "PROPOSAL", "COMMENT"

    @Column(nullable = false)
    val entityId: UUID,

    @Column(nullable = false, columnDefinition = "vector")
    @JdbcTypeCode(SqlTypes.VECTOR)
    val embedding: FloatArray,

    @Column(nullable = false)
    val model: String,

    @Column(nullable = false)
    val dimension: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmbeddingVector

        if (id != other.id) return false
        if (entityType != other.entityType) return false
        if (entityId != other.entityId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + entityType.hashCode()
        result = 31 * result + entityId.hashCode()
        return result
    }
}
