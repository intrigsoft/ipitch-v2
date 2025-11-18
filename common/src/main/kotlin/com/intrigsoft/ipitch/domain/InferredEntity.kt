package com.intrigsoft.ipitch.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "inferred_entities",
    indexes = [
        Index(name = "idx_inferred_proposal", columnList = "proposalId"),
        Index(name = "idx_inferred_comment", columnList = "sourceCommentId"),
        Index(name = "idx_inferred_type", columnList = "entityType"),
        Index(name = "idx_inferred_status", columnList = "status")
    ]
)
data class InferredEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val proposalId: UUID,

    @Column(nullable = false)
    val sourceCommentId: UUID,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val entityType: InferredEntityType,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var summary: String,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: InferredEntityStatus = InferredEntityStatus.PENDING,

    // AI confidence score (0.0 to 1.0)
    @Column(nullable = false)
    val confidenceScore: Double,

    // Additional metadata (e.g., extracted keywords, sentiment, etc.)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var metadata: Map<String, Any> = emptyMap(),

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    // User who reviewed/approved the inferred entity (Keycloak user ID)
    @Column
    var reviewedBy: String? = null,

    @Column
    var reviewedAt: LocalDateTime? = null
)

enum class InferredEntityType {
    SUGGESTION,
    CONCERN,
    QUESTION,
    REQUIREMENT
}

enum class InferredEntityStatus {
    PENDING,      // Awaiting review
    APPROVED,     // Reviewed and approved
    REJECTED,     // Reviewed and rejected
    ADDRESSED     // Action has been taken
}
