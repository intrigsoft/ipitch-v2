package com.intrigsoft.ipitch.proposalmanager.dto.response

import com.intrigsoft.ipitch.domain.ProposalSuggestion
import java.time.Instant
import java.util.UUID

/**
 * Response DTO for a proposal suggestion
 */
data class SuggestionResponse(
    val id: UUID,
    val proposalId: UUID,
    val text: String,
    val commentCount: Int,
    val commentIds: List<UUID>,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(suggestion: ProposalSuggestion): SuggestionResponse {
            return SuggestionResponse(
                id = suggestion.id,
                proposalId = suggestion.proposalId,
                text = suggestion.text,
                commentCount = suggestion.comments.size,
                commentIds = suggestion.comments.map { it.commentId },
                createdAt = suggestion.createdAt,
                updatedAt = suggestion.updatedAt
            )
        }
    }
}
