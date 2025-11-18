package com.intrigsoft.ipitch.proposalmanager.dto.response

import com.intrigsoft.ipitch.domain.ProposalConcern
import java.time.Instant
import java.util.UUID

/**
 * Response DTO for a proposal concern
 */
data class ConcernResponse(
    val id: UUID,
    val proposalId: UUID,
    val text: String,
    val commentCount: Int,
    val commentIds: List<UUID>,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(concern: ProposalConcern): ConcernResponse {
            return ConcernResponse(
                id = concern.id,
                proposalId = concern.proposalId,
                text = concern.text,
                commentCount = concern.comments.size,
                commentIds = concern.comments.map { it.commentId },
                createdAt = concern.createdAt,
                updatedAt = concern.updatedAt
            )
        }
    }
}
