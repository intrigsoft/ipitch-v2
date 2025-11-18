package com.intrigsoft.ipitch.aiintegration.model

import java.time.Instant
import java.util.UUID

/**
 * Complete analysis result for a proposal
 */
data class ProposalAnalysisResult(
    val proposalId: UUID,
    val summary: String,
    val clarityScore: Double,  // 0.0 to 10.0
    val sectorScores: List<SectorScore>,
    val embeddingId: UUID? = null,  // Reference to vector embedding
    val analyzedAt: Instant = Instant.now(),
    val model: String,
    val provider: AIProvider
) {
    init {
        require(clarityScore in 0.0..10.0) { "Clarity score must be between 0.0 and 10.0" }
    }
}
