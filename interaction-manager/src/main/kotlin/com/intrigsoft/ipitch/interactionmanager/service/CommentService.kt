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
    private val commentAnalysisService: CommentAnalysisService? = null,
    private val userRepository: com.intrigsoft.ipitch.repository.UserRepository,
    private val suggestionConcernService: com.intrigsoft.ipitch.aiintegration.service.SuggestionConcernService? = null
) {

    @Transactional
    fun createComment(request: CreateCommentRequest): CommentResponse {
        val startTime = System.currentTimeMillis()
        logger.info { "[COMMENT] Creating comment - targetType: ${request.targetType}, targetId: ${request.targetId}, userId: ${request.userId}, contentLength: ${request.content.length}, parentId: ${request.parentCommentId}" }

        // Validate parent comment if provided
        val parentComment = request.parentCommentId?.let {
            val parent = commentRepository.findById(it).orElseThrow {
                throw InvalidOperationException("Parent comment not found with id: $it")
            }
            logger.debug { "[COMMENT] Parent comment found - id: $it, userId: ${parent.userId}" }
            parent
        }

        // Validate that parent comment is not deleted
        if (parentComment?.deleted == true) {
            logger.warn { "[COMMENT] Attempted to reply to deleted comment - parentId: ${parentComment.id}" }
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
        logger.info { "[COMMENT] Comment created - id: ${savedComment.id}, userId: ${savedComment.userId}, targetType: ${savedComment.targetType}" }

        // Mark user as dirty for score recalculation
        markUserAsDirty(savedComment.userId)

        // AI Analysis: Perform governance check and content analysis
        try {
            commentAnalysisService?.let { analysisService ->
                runBlocking {
                    logger.info { "[COMMENT] Starting AI analysis - commentId: ${savedComment.id}" }

                    // Get the proposal for context
                    val proposal = getProposalForComment(savedComment)

                    // Build comment thread for context (if this is a reply)
                    val commentThread = buildCommentThread(savedComment)

                    if (proposal != null) {
                        logger.debug { "[COMMENT] Proposal context - proposalId: ${proposal.id}, title: '${proposal.title}'" }
                        val analysisResult = analysisService.analyzeComment(savedComment, proposal, commentThread)

                        if (analysisResult.isFlagged) {
                            logger.warn { "[COMMENT] Comment flagged - commentId: ${savedComment.id}, reason: ${analysisResult.flagReason}, flags: ${analysisResult.governanceFlags}" }
                        } else {
                            logger.info { "[COMMENT] AI analysis completed - commentId: ${savedComment.id}, relevance: ${analysisResult.relevanceScore}, mode: ${analysisResult.mode}, marketing: ${analysisResult.isMarketing}" }

                            // Extract suggestions and concerns from the comment
                            extractSuggestionsAndConcerns(savedComment, proposal, commentThread)
                        }
                    } else {
                        logger.warn { "[COMMENT] No proposal context found for comment ${savedComment.id}, skipping AI analysis" }
                    }
                }
            } ?: logger.warn { "[COMMENT] CommentAnalysisService not available, skipping AI analysis for comment ${savedComment.id}" }
        } catch (e: Exception) {
            logger.error(e) { "[COMMENT] AI analysis failed for comment ${savedComment.id}, error: ${e.message}" }
            // Don't fail the comment creation if AI analysis fails
        }

        // Sync to Elasticsearch asynchronously
        elasticsearchSyncService.syncComment(savedComment)

        val duration = System.currentTimeMillis() - startTime
        logger.info { "[COMMENT] Comment creation completed - id: ${savedComment.id}, duration: ${duration}ms" }

        return toResponse(savedComment, request.userId)
    }

    @Transactional
    fun updateComment(commentId: UUID, request: UpdateCommentRequest, userId: String): CommentResponse {
        val startTime = System.currentTimeMillis()
        logger.info { "[COMMENT] Updating comment - commentId: $commentId, userId: $userId, newContentLength: ${request.content.length}" }

        val comment = commentRepository.findById(commentId).orElseThrow {
            throw CommentNotFoundException(commentId)
        }

        val oldContent = comment.content
        val oldContentLength = oldContent.length

        // Check authorization
        if (comment.userId != userId) {
            logger.warn { "[COMMENT] Unauthorized update attempt - commentId: $commentId, attemptedBy: $userId, owner: ${comment.userId}" }
            throw UnauthorizedOperationException("You are not authorized to update this comment")
        }

        if (comment.deleted) {
            logger.warn { "[COMMENT] Attempted to update deleted comment - commentId: $commentId" }
            throw InvalidOperationException("Cannot update a deleted comment")
        }

        comment.content = request.content
        comment.updatedAt = LocalDateTime.now()

        val updatedComment = commentRepository.save(comment)
        logger.info { "[COMMENT] Comment updated - commentId: $commentId, oldLength: $oldContentLength, newLength: ${request.content.length}" }

        // Mark user as dirty for score recalculation
        markUserAsDirty(updatedComment.userId)

        // Sync to Elasticsearch
        elasticsearchSyncService.syncComment(updatedComment)

        val duration = System.currentTimeMillis() - startTime
        logger.info { "[COMMENT] Comment update completed - commentId: $commentId, duration: ${duration}ms" }

        return toResponse(updatedComment, userId)
    }

    @Transactional
    fun deleteComment(commentId: UUID, userId: String): CommentResponse {
        val startTime = System.currentTimeMillis()
        logger.info { "[COMMENT] Deleting comment - commentId: $commentId, userId: $userId" }

        val comment = commentRepository.findById(commentId).orElseThrow {
            throw CommentNotFoundException(commentId)
        }

        logger.debug { "[COMMENT] Comment details - id: $commentId, owner: ${comment.userId}, targetType: ${comment.targetType}, targetId: ${comment.targetId}" }

        // Check authorization
        if (comment.userId != userId) {
            logger.warn { "[COMMENT] Unauthorized delete attempt - commentId: $commentId, attemptedBy: $userId, owner: ${comment.userId}" }
            throw UnauthorizedOperationException("You are not authorized to delete this comment")
        }

        // Soft delete
        comment.deleted = true
        comment.updatedAt = LocalDateTime.now()

        val deletedComment = commentRepository.save(comment)
        logger.info { "[COMMENT] Comment soft deleted - commentId: $commentId, userId: ${deletedComment.userId}" }

        // Sync to Elasticsearch
        elasticsearchSyncService.syncComment(deletedComment)

        val duration = System.currentTimeMillis() - startTime
        logger.info { "[COMMENT] Comment deletion completed - commentId: $commentId, duration: ${duration}ms" }

        return toResponse(deletedComment, userId)
    }

    @Transactional(readOnly = true)
    fun getComment(commentId: UUID, userId: String?): CommentResponse {
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
        userId: String?
    ): List<CommentResponse> {
        logger.debug { "Fetching comments for target $targetType:$targetId" }

        val comments = commentRepository.findByTargetTypeAndTargetIdAndParentCommentIsNullAndDeletedFalse(
            targetType,
            targetId
        )

        return comments.map { toResponse(it, userId) }
    }

    @Transactional(readOnly = true)
    fun getReplies(commentId: UUID, userId: String?): List<CommentResponse> {
        logger.debug { "Fetching replies for comment $commentId" }

        val comment = commentRepository.findById(commentId).orElseThrow {
            throw CommentNotFoundException(commentId)
        }

        val replies = commentRepository.findByParentCommentAndDeletedFalse(comment)

        return replies.map { toResponse(it, userId) }
    }

    @Transactional(readOnly = true)
    fun getUserComments(userId: String): List<CommentResponse> {
        logger.debug { "Fetching comments for user $userId" }

        val comments = commentRepository.findByUserIdAndDeletedFalse(userId)

        return comments.map { toResponse(it, userId) }
    }

    private fun toResponse(comment: Comment, userId: String?): CommentResponse {
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

    /**
     * Marks a user as dirty to trigger score recalculation
     */
    private fun markUserAsDirty(userId: String) {
        try {
            userRepository.findById(userId).ifPresent { user ->
                user.dirty = true
                userRepository.save(user)
                logger.info { "Marked user $userId as dirty for score recalculation" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to mark user $userId as dirty, but comment operation was successful" }
            // Don't fail the comment operation if marking dirty fails
        }
    }

    /**
     * Extract suggestions and concerns from a comment and store them
     * This is called after comment analysis if the comment is not flagged
     */
    private suspend fun extractSuggestionsAndConcerns(
        comment: Comment,
        proposal: Proposal,
        commentThread: List<Comment>
    ) {
        try {
            suggestionConcernService?.let { service ->
                logger.info { "Extracting suggestions and concerns from comment ${comment.id}" }

                // Extract suggestions and concerns
                val extraction = service.extractSuggestionsAndConcerns(comment, proposal, commentThread)

                logger.info { "Extracted ${extraction.suggestions.size} suggestions and ${extraction.concerns.size} concerns from comment ${comment.id}" }

                // Process suggestions with similarity detection
                if (extraction.suggestions.isNotEmpty()) {
                    val suggestionResults = service.processSuggestions(
                        proposalId = proposal.id!!,
                        commentId = comment.id!!,
                        suggestions = extraction.suggestions
                    )

                    suggestionResults.forEach { result ->
                        if (result.isNew) {
                            logger.info { "Created new suggestion ${result.suggestionId} for proposal ${proposal.id}" }
                        } else {
                            logger.info { "Linked to existing suggestion ${result.suggestionId} (similarity: ${result.similarityScore})" }
                        }
                    }
                }

                // Process concerns with similarity detection
                if (extraction.concerns.isNotEmpty()) {
                    val concernResults = service.processConcerns(
                        proposalId = proposal.id!!,
                        commentId = comment.id!!,
                        concerns = extraction.concerns
                    )

                    concernResults.forEach { result ->
                        if (result.isNew) {
                            logger.info { "Created new concern ${result.concernId} for proposal ${proposal.id}" }
                        } else {
                            logger.info { "Linked to existing concern ${result.concernId} (similarity: ${result.similarityScore})" }
                        }
                    }
                }

                logger.info { "Successfully processed suggestions and concerns for comment ${comment.id}" }
            } ?: logger.warn { "SuggestionConcernService not available, skipping suggestion/concern extraction" }
        } catch (e: Exception) {
            logger.error(e) { "Error extracting suggestions/concerns from comment ${comment.id}, but comment was saved successfully" }
            // Don't fail the comment creation if extraction fails
        }
    }
}
