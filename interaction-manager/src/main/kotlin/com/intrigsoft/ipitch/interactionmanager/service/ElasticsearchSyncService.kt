package com.intrigsoft.ipitch.interactionmanager.service

import com.intrigsoft.ipitch.domain.*
import com.intrigsoft.ipitch.interactionmanager.document.CommentDocument
import com.intrigsoft.ipitch.interactionmanager.document.InferredEntityDocument
import com.intrigsoft.ipitch.interactionmanager.document.ProposalDocument
import com.intrigsoft.ipitch.interactionmanager.exception.ElasticsearchSyncException
import com.intrigsoft.ipitch.interactionmanager.search.CommentSearchRepository
import com.intrigsoft.ipitch.interactionmanager.search.InferredEntitySearchRepository
import com.intrigsoft.ipitch.interactionmanager.search.ProposalSearchRepository
import com.intrigsoft.ipitch.repository.CommentRepository
import com.intrigsoft.ipitch.repository.VoteRepository
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.*

private val logger = KotlinLogging.logger {}

@Service
class ElasticsearchSyncService(
    private val commentSearchRepository: CommentSearchRepository,
    private val proposalSearchRepository: ProposalSearchRepository,
    private val inferredEntitySearchRepository: InferredEntitySearchRepository,
    private val voteRepository: VoteRepository,
    private val commentRepository: CommentRepository
) {

    @Async
    fun syncComment(comment: Comment) {
        try {
            logger.debug { "Syncing comment ${comment.id} to Elasticsearch" }

            val voteStats = getVoteStats(VoteTargetType.COMMENT, comment.id!!)
            val replyCount = commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(
                CommentTargetType.COMMENT,
                comment.id!!
            )

            val document = CommentDocument(
                id = comment.id.toString(),
                userId = comment.userId.toString(),
                content = comment.content,
                parentCommentId = comment.parentComment?.id?.toString(),
                targetType = comment.targetType.name,
                targetId = comment.targetId.toString(),
                upvotes = voteStats.first,
                downvotes = voteStats.second,
                voteScore = voteStats.third,
                replyCount = replyCount,
                createdAt = comment.createdAt,
                updatedAt = comment.updatedAt,
                deleted = comment.deleted
            )

            commentSearchRepository.save(document)
            logger.debug { "Comment ${comment.id} synced to Elasticsearch" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to sync comment ${comment.id} to Elasticsearch" }
            throw ElasticsearchSyncException("Failed to sync comment to Elasticsearch", e)
        }
    }

    @Async
    fun syncProposal(proposal: Proposal) {
        try {
            logger.debug { "Syncing proposal ${proposal.id} to Elasticsearch" }

            val voteStats = getVoteStats(VoteTargetType.PROPOSAL, proposal.id!!)
            val commentCount = commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(
                CommentTargetType.PROPOSAL,
                proposal.id!!
            )

            val document = ProposalDocument(
                id = proposal.id.toString(),
                title = proposal.title,
                content = proposal.content,
                ownerId = proposal.ownerId.toString(),
                version = proposal.version,
                status = proposal.status.name,
                upvotes = voteStats.first,
                downvotes = voteStats.second,
                voteScore = voteStats.third,
                commentCount = commentCount,
                createdAt = proposal.createdAt,
                updatedAt = proposal.updatedAt
            )

            proposalSearchRepository.save(document)
            logger.debug { "Proposal ${proposal.id} synced to Elasticsearch" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to sync proposal ${proposal.id} to Elasticsearch" }
            throw ElasticsearchSyncException("Failed to sync proposal to Elasticsearch", e)
        }
    }

    @Async
    fun syncInferredEntity(entity: InferredEntity) {
        try {
            logger.debug { "Syncing inferred entity ${entity.id} to Elasticsearch" }

            val voteStats = getVoteStats(VoteTargetType.INFERRED_ENTITY, entity.id!!)
            val commentCount = commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(
                CommentTargetType.INFERRED_ENTITY,
                entity.id!!
            )

            val document = InferredEntityDocument(
                id = entity.id.toString(),
                proposalId = entity.proposalId.toString(),
                sourceCommentId = entity.sourceCommentId.toString(),
                entityType = entity.entityType.name,
                content = entity.content,
                summary = entity.summary,
                status = entity.status.name,
                confidenceScore = entity.confidenceScore,
                upvotes = voteStats.first,
                downvotes = voteStats.second,
                voteScore = voteStats.third,
                commentCount = commentCount,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                reviewedBy = entity.reviewedBy?.toString(),
                reviewedAt = entity.reviewedAt
            )

            inferredEntitySearchRepository.save(document)
            logger.debug { "Inferred entity ${entity.id} synced to Elasticsearch" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to sync inferred entity ${entity.id} to Elasticsearch" }
            throw ElasticsearchSyncException("Failed to sync inferred entity to Elasticsearch", e)
        }
    }

    private fun getVoteStats(targetType: VoteTargetType, targetId: UUID): Triple<Long, Long, Long> {
        val upvotes = voteRepository.countUpvotes(targetType, targetId)
        val downvotes = voteRepository.countDownvotes(targetType, targetId)
        val score = voteRepository.getVoteScore(targetType, targetId) ?: 0L
        return Triple(upvotes, downvotes, score)
    }
}
