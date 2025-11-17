package com.intrigsoft.ipitch.interactionmanager.dto.response

import com.intrigsoft.ipitch.domain.InferredEntity
import com.intrigsoft.ipitch.domain.InferredEntityStatus
import com.intrigsoft.ipitch.domain.InferredEntityType
import java.time.LocalDateTime
import java.util.*

data class InferredEntityResponse(
    val id: UUID,
    val proposalId: UUID,
    val sourceCommentId: UUID,
    val entityType: InferredEntityType,
    val content: String,
    val summary: String,
    val status: InferredEntityStatus,
    val confidenceScore: Double,
    val metadata: Map<String, Any>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val reviewedBy: UUID?,
    val reviewedAt: LocalDateTime?,
    val voteStats: VoteStatsResponse,
    val commentCount: Long = 0
) {
    companion object {
        fun from(
            entity: InferredEntity,
            voteStats: VoteStatsResponse,
            commentCount: Long = 0
        ): InferredEntityResponse {
            return InferredEntityResponse(
                id = entity.id!!,
                proposalId = entity.proposalId,
                sourceCommentId = entity.sourceCommentId,
                entityType = entity.entityType,
                content = entity.content,
                summary = entity.summary,
                status = entity.status,
                confidenceScore = entity.confidenceScore,
                metadata = entity.metadata,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                reviewedBy = entity.reviewedBy,
                reviewedAt = entity.reviewedAt,
                voteStats = voteStats,
                commentCount = commentCount
            )
        }
    }
}
