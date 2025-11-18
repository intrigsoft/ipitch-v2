package com.intrigsoft.ipitch.aiintegration.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Persistent entity for storing comment AI analysis results
 */
@Entity
@Table(name = "comment_analysis")
data class CommentAnalysis(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false, unique = true)
    val commentId: UUID,

    // Governance analysis
    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    val governanceFlags: List<GovernanceFlag>,

    @Column(nullable = false)
    val governanceScore: Double,

    @Column(nullable = false)
    val isFlagged: Boolean,

    @Column(length = 1000)
    val flagReason: String? = null,

    // Content analysis (only if not flagged)
    @Column
    val relevanceScore: Double? = null,

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    val sectorScores: List<SectorScore>? = null,

    @Enumerated(EnumType.STRING)
    @Column
    val mode: ContentMode? = null,

    @Column(nullable = false)
    val isMarketing: Boolean = false,

    @Column
    val marketingScore: Double? = null,

    @Column(nullable = false)
    val model: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: AIProvider,

    @Column(name = "analyzed_at", nullable = false)
    val analyzedAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
