package com.intrigsoft.ipitch.aiintegration.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Persistent entity for storing proposal AI analysis results
 */
@Entity
@Table(name = "proposal_analysis")
data class ProposalAnalysis(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false, unique = true)
    val proposalId: UUID,

    @Column(nullable = false, length = 2000)
    val summary: String,

    @Column(nullable = false)
    val clarityScore: Double,

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    val sectorScores: List<SectorScore>,

    @Column
    val embeddingId: UUID? = null,

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
