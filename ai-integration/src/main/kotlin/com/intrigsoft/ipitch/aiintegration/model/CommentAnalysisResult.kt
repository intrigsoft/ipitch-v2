package com.intrigsoft.ipitch.aiintegration.model

import java.time.Instant
import java.util.UUID

/**
 * Complete analysis result for a comment
 */
data class CommentAnalysisResult(
    val commentId: UUID,
    val governanceFlags: List<GovernanceFlag>,
    val governanceScore: Double,  // 0.0 to 1.0 (higher = more problematic)
    val isFlagged: Boolean,
    val flagReason: String? = null,

    // Additional analysis (only performed if governance passes)
    val relevanceScore: Double? = null,  // 0.0 to 10.0
    val sectorScores: List<SectorScore>? = null,
    val mode: ContentMode? = null,
    val isMarketing: Boolean = false,
    val marketingScore: Double? = null,  // 0.0 to 1.0

    val analyzedAt: Instant = Instant.now(),
    val model: String,
    val provider: AIProvider
) {
    init {
        require(governanceScore in 0.0..1.0) { "Governance score must be between 0.0 and 1.0" }
        relevanceScore?.let { require(it in 0.0..10.0) { "Relevance score must be between 0.0 and 10.0" } }
        marketingScore?.let { require(it in 0.0..1.0) { "Marketing score must be between 0.0 and 1.0" } }
    }
}
