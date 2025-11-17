package com.intrigsoft.ipitch.aiintegration.model

import jakarta.persistence.Embeddable

/**
 * Represents a sector-specific score for a proposal or comment
 */
@Embeddable
data class SectorScore(
    val sector: String,
    val score: Double  // 0.0 to 10.0
) {
    init {
        require(score in 0.0..10.0) { "Score must be between 0.0 and 10.0" }
    }
}
