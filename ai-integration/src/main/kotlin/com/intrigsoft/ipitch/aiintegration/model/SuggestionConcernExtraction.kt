package com.intrigsoft.ipitch.aiintegration.model

import java.util.UUID

/**
 * Result of extracting suggestions and concerns from a comment
 */
data class SuggestionConcernExtractionResult(
    val commentId: UUID,
    val suggestions: List<String>,
    val concerns: List<String>
)

/**
 * Result of processing a suggestion or concern with similarity detection
 */
data class SuggestionProcessingResult(
    val suggestionId: UUID,
    val text: String,
    val isNew: Boolean,
    val similarityScore: Double?
)

data class ConcernProcessingResult(
    val concernId: UUID,
    val text: String,
    val isNew: Boolean,
    val similarityScore: Double?
)
