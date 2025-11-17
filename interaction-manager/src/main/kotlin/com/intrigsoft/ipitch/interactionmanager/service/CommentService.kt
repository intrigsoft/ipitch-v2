package com.intrigsoft.ipitch.interactionmanager.service

import com.intrigsoft.ipitch.domain.Comment
import com.intrigsoft.ipitch.domain.CommentTargetType
import com.intrigsoft.ipitch.interactionmanager.dto.request.CreateCommentRequest
import com.intrigsoft.ipitch.interactionmanager.dto.request.UpdateCommentRequest
import com.intrigsoft.ipitch.interactionmanager.dto.response.CommentResponse
import com.intrigsoft.ipitch.interactionmanager.dto.response.VoteStatsResponse
import com.intrigsoft.ipitch.interactionmanager.exception.CommentNotFoundException
import com.intrigsoft.ipitch.interactionmanager.exception.InvalidOperationException
import com.intrigsoft.ipitch.interactionmanager.exception.UnauthorizedOperationException
import com.intrigsoft.ipitch.repository.CommentRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

private val logger = KotlinLogging.logger {}

@Service
class CommentService(
    private val commentRepository: CommentRepository,
    private val voteService: VoteService,
    private val elasticsearchSyncService: ElasticsearchSyncService
) {

    @Transactional
    fun createComment(request: CreateCommentRequest): CommentResponse {
        logger.info { "Creating comment for target ${request.targetType}:${request.targetId} by user ${request.userId}" }

        // Validate parent comment if provided
        val parentComment = request.parentCommentId?.let {
            commentRepository.findById(it).orElseThrow {
                throw InvalidOperationException("Parent comment not found with id: $it")
            }
        }

        // Validate that parent comment is not deleted
        if (parentComment?.deleted == true) {
            throw InvalidOperationException("Cannot reply to a deleted comment")
        }

        val comment = Comment(
            userId = request.userId,
            content = request.content,
            parentComment = parentComment,
            targetType = request.targetType,
            targetId = request.targetId
        )

        val savedComment = commentRepository.save(comment)
        logger.info { "Comment created with id: ${savedComment.id}" }

        // Sync to Elasticsearch asynchronously
        elasticsearchSyncService.syncComment(savedComment)

        return toResponse(savedComment, request.userId)
    }

    @Transactional
    fun updateComment(commentId: UUID, request: UpdateCommentRequest, userId: UUID): CommentResponse {
        logger.info { "Updating comment $commentId by user $userId" }

        val comment = commentRepository.findById(commentId).orElseThrow {
            throw CommentNotFoundException(commentId)
        }

        // Check authorization
        if (comment.userId != userId) {
            throw UnauthorizedOperationException("You are not authorized to update this comment")
        }

        if (comment.deleted) {
            throw InvalidOperationException("Cannot update a deleted comment")
        }

        comment.content = request.content
        comment.updatedAt = LocalDateTime.now()

        val updatedComment = commentRepository.save(comment)
        logger.info { "Comment updated: $commentId" }

        // Sync to Elasticsearch
        elasticsearchSyncService.syncComment(updatedComment)

        return toResponse(updatedComment, userId)
    }

    @Transactional
    fun deleteComment(commentId: UUID, userId: UUID): CommentResponse {
        logger.info { "Deleting comment $commentId by user $userId" }

        val comment = commentRepository.findById(commentId).orElseThrow {
            throw CommentNotFoundException(commentId)
        }

        // Check authorization
        if (comment.userId != userId) {
            throw UnauthorizedOperationException("You are not authorized to delete this comment")
        }

        // Soft delete
        comment.deleted = true
        comment.updatedAt = LocalDateTime.now()

        val deletedComment = commentRepository.save(comment)
        logger.info { "Comment soft deleted: $commentId" }

        // Sync to Elasticsearch
        elasticsearchSyncService.syncComment(deletedComment)

        return toResponse(deletedComment, userId)
    }

    @Transactional(readOnly = true)
    fun getComment(commentId: UUID, userId: UUID?): CommentResponse {
        logger.debug { "Fetching comment $commentId" }

        val comment = commentRepository.findById(commentId).orElseThrow {
            throw CommentNotFoundException(commentId)
        }

        return toResponse(comment, userId)
    }

    @Transactional(readOnly = true)
    fun getCommentsByTarget(
        targetType: CommentTargetType,
        targetId: UUID,
        userId: UUID?
    ): List<CommentResponse> {
        logger.debug { "Fetching comments for target $targetType:$targetId" }

        val comments = commentRepository.findByTargetTypeAndTargetIdAndParentCommentIsNullAndDeletedFalse(
            targetType,
            targetId
        )

        return comments.map { toResponse(it, userId) }
    }

    @Transactional(readOnly = true)
    fun getReplies(commentId: UUID, userId: UUID?): List<CommentResponse> {
        logger.debug { "Fetching replies for comment $commentId" }

        val comment = commentRepository.findById(commentId).orElseThrow {
            throw CommentNotFoundException(commentId)
        }

        val replies = commentRepository.findByParentCommentAndDeletedFalse(comment)

        return replies.map { toResponse(it, userId) }
    }

    @Transactional(readOnly = true)
    fun getUserComments(userId: UUID): List<CommentResponse> {
        logger.debug { "Fetching comments for user $userId" }

        val comments = commentRepository.findByUserIdAndDeletedFalse(userId)

        return comments.map { toResponse(it, userId) }
    }

    private fun toResponse(comment: Comment, userId: UUID?): CommentResponse {
        val voteStats = voteService.getVoteStats(
            com.intrigsoft.ipitch.domain.VoteTargetType.COMMENT,
            comment.id!!,
            userId
        )

        val replyCount = commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(
            CommentTargetType.COMMENT,
            comment.id!!
        )

        return CommentResponse.from(comment, voteStats, replyCount)
    }
}
