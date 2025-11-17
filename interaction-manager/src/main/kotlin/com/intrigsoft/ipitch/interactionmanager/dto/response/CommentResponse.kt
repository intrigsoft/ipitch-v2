package com.intrigsoft.ipitch.interactionmanager.dto.response

import com.intrigsoft.ipitch.domain.Comment
import com.intrigsoft.ipitch.domain.CommentTargetType
import java.time.LocalDateTime
import java.util.*

data class CommentResponse(
    val id: UUID,
    val userId: UUID,
    val content: String,
    val parentCommentId: UUID?,
    val targetType: CommentTargetType,
    val targetId: UUID,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deleted: Boolean,
    val voteStats: VoteStatsResponse,
    val replyCount: Long = 0
) {
    companion object {
        fun from(comment: Comment, voteStats: VoteStatsResponse, replyCount: Long = 0): CommentResponse {
            return CommentResponse(
                id = comment.id!!,
                userId = comment.userId,
                content = comment.content,
                parentCommentId = comment.parentComment?.id,
                targetType = comment.targetType,
                targetId = comment.targetId,
                createdAt = comment.createdAt,
                updatedAt = comment.updatedAt,
                deleted = comment.deleted,
                voteStats = voteStats,
                replyCount = replyCount
            )
        }
    }
}

data class VoteStatsResponse(
    val upvotes: Long = 0,
    val downvotes: Long = 0,
    val score: Long = 0,
    val userVote: String? = null  // "UP", "DOWN", or null
)
