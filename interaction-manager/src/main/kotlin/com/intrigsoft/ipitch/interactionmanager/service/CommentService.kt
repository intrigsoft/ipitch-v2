package com.intrigsoft.ipitch.interactionmanager.service

import com.intrigsoft.ipitch.domain.Comment
import com.intrigsoft.ipitch.domain.CommentTargetType
import com.intrigsoft.ipitch.domain.Proposal
import com.intrigsoft.ipitch.interactionmanager.dto.request.CreateCommentRequest
import com.intrigsoft.ipitch.interactionmanager.dto.request.UpdateCommentRequest
import com.intrigsoft.ipitch.interactionmanager.dto.response.CommentResponse
import com.intrigsoft.ipitch.interactionmanager.dto.response.VoteStatsResponse
import com.intrigsoft.ipitch.interactionmanager.exception.CommentNotFoundException
import com.intrigsoft.ipitch.interactionmanager.exception.InvalidOperationException
import com.intrigsoft.ipitch.interactionmanager.exception.UnauthorizedOperationException
import com.intrigsoft.ipitch.repository.CommentRepository
import com.intrigsoft.ipitch.repository.ProposalRepository
import com.intrigsoft.ipitch.aiintegration.service.CommentAnalysisService
import kotlinx.coroutines.runBlocking
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
    private val elasticsearchSyncService: ElasticsearchSyncService,
    private val proposalRepository: ProposalRepository,
    private val commentAnalysisService: CommentAnalysisService? = null
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

        // AI Analysis: Perform governance check and content analysis
        try {
            commentAnalysisService?.let { analysisService ->
                runBlocking {
                    logger.info { "Starting AI analysis for comment ${savedComment.id}" }

                    // Get the proposal for context
                    val proposal = getProposalForComment(savedComment)

                    // Build comment thread for context (if this is a reply)
                    val commentThread = buildCommentThread(savedComment)

                    if (proposal != null) {
                        val analysisResult = analysisService.analyzeComment(savedComment, proposal, commentThread)

                        if (analysisResult.isFlagged) {
                            logger.warn { "Comment ${savedComment.id} flagged: ${analysisResult.flagReason}" }
                            logger.info { "Governance flags: ${analysisResult.governanceFlags}" }
                        } else {
                            logger.info { "Comment ${savedComment.id} analysis completed successfully" }
                            logger.info { "Relevance: ${analysisResult.relevanceScore}, Mode: ${analysisResult.mode}, Marketing: ${analysisResult.isMarketing}" }
                        }
                    } else {
                        logger.warn { "Could not find proposal for comment ${savedComment.id}, skipping AI analysis" }
                    }
                }
            } ?: logger.warn { "CommentAnalysisService not available, skipping AI analysis" }
        } catch (e: Exception) {
            logger.error(e) { "Error during AI analysis for comment ${savedComment.id}, but comment was saved successfully" }
            // Don't fail the comment creation if AI analysis fails
        }

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

    /**
     * Get the proposal that this comment is ultimately about
     * Walks up the comment chain if needed
     */
    private fun getProposalForComment(comment: Comment): Proposal? {
        return when (comment.targetType) {
            CommentTargetType.PROPOSAL -> {
                // Direct comment on proposal
                proposalRepository.findById(comment.targetId).orElse(null)
            }
            CommentTargetType.COMMENT -> {
                // Comment on another comment - walk up the chain
                var currentComment: Comment? = comment
                while (currentComment != null && currentComment.targetType == CommentTargetType.COMMENT) {
                    currentComment = commentRepository.findById(currentComment.targetId).orElse(null)
                }
                if (currentComment?.targetType == CommentTargetType.PROPOSAL) {
                    proposalRepository.findById(currentComment.targetId).orElse(null)
                } else {
                    null
                }
            }
            CommentTargetType.INFERRED_ENTITY -> {
                // For inferred entities, we'd need to look up the entity and get its proposal
                // For now, skip AI analysis for these
                null
            }
        }
    }

    /**
     * Build the complete comment thread from root to this comment
     * This provides context for AI analysis
     */
    private fun buildCommentThread(comment: Comment): List<Comment> {
        val thread = mutableListOf<Comment>()

        // If this is a reply to another comment, walk up the chain
        var currentComment = comment.parentComment
        while (currentComment != null) {
            thread.add(0, currentComment)  // Add to beginning to maintain order
            currentComment = currentComment.parentComment
        }

        return thread
    }
}
