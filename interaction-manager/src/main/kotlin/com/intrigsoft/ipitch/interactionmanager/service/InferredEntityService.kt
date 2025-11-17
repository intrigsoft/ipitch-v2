package com.intrigsoft.ipitch.interactionmanager.service

import com.intrigsoft.ipitch.domain.InferredEntity
import com.intrigsoft.ipitch.domain.InferredEntityStatus
import com.intrigsoft.ipitch.domain.InferredEntityType
import com.intrigsoft.ipitch.domain.VoteTargetType
import com.intrigsoft.ipitch.interactionmanager.dto.response.InferredEntityResponse
import com.intrigsoft.ipitch.interactionmanager.exception.InferredEntityNotFoundException
import com.intrigsoft.ipitch.repository.CommentRepository
import com.intrigsoft.ipitch.repository.InferredEntityRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

private val logger = KotlinLogging.logger {}

@Service
class InferredEntityService(
    private val inferredEntityRepository: InferredEntityRepository,
    private val commentRepository: CommentRepository,
    private val voteService: VoteService,
    private val elasticsearchSyncService: ElasticsearchSyncService
) {

    @Transactional(readOnly = true)
    fun getInferredEntity(id: UUID, userId: UUID?): InferredEntityResponse {
        logger.debug { "Fetching inferred entity $id" }

        val entity = inferredEntityRepository.findById(id).orElseThrow {
            throw InferredEntityNotFoundException(id)
        }

        return toResponse(entity, userId)
    }

    @Transactional(readOnly = true)
    fun getInferredEntitiesByProposal(
        proposalId: UUID,
        userId: UUID?,
        entityType: InferredEntityType? = null,
        status: InferredEntityStatus? = null
    ): List<InferredEntityResponse> {
        logger.debug { "Fetching inferred entities for proposal $proposalId" }

        val entities = when {
            entityType != null && status != null ->
                inferredEntityRepository.findByProposalIdAndEntityTypeAndStatusOrderByConfidenceDesc(
                    proposalId, entityType, status
                )
            entityType != null ->
                inferredEntityRepository.findByProposalIdAndEntityType(proposalId, entityType)
            status != null ->
                inferredEntityRepository.findByProposalIdAndStatus(proposalId, status)
            else ->
                inferredEntityRepository.findByProposalId(proposalId)
        }

        return entities.map { toResponse(it, userId) }
    }

    @Transactional(readOnly = true)
    fun getInferredEntitiesByComment(commentId: UUID, userId: UUID?): List<InferredEntityResponse> {
        logger.debug { "Fetching inferred entities for comment $commentId" }

        val entities = inferredEntityRepository.findBySourceCommentId(commentId)

        return entities.map { toResponse(it, userId) }
    }

    @Transactional
    fun updateStatus(
        id: UUID,
        status: InferredEntityStatus,
        reviewerId: UUID
    ): InferredEntityResponse {
        logger.info { "Updating inferred entity $id status to $status" }

        val entity = inferredEntityRepository.findById(id).orElseThrow {
            throw InferredEntityNotFoundException(id)
        }

        entity.status = status
        entity.reviewedBy = reviewerId
        entity.reviewedAt = LocalDateTime.now()
        entity.updatedAt = LocalDateTime.now()

        val updatedEntity = inferredEntityRepository.save(entity)
        logger.info { "Inferred entity $id status updated to $status" }

        // Sync to Elasticsearch
        elasticsearchSyncService.syncInferredEntity(updatedEntity)

        return toResponse(updatedEntity, reviewerId)
    }

    @Transactional
    fun updateContent(
        id: UUID,
        content: String,
        summary: String
    ): InferredEntityResponse {
        logger.info { "Updating inferred entity $id content" }

        val entity = inferredEntityRepository.findById(id).orElseThrow {
            throw InferredEntityNotFoundException(id)
        }

        entity.content = content
        entity.summary = summary
        entity.updatedAt = LocalDateTime.now()

        val updatedEntity = inferredEntityRepository.save(entity)
        logger.info { "Inferred entity $id content updated" }

        // Sync to Elasticsearch
        elasticsearchSyncService.syncInferredEntity(updatedEntity)

        return toResponse(updatedEntity, null)
    }

    private fun toResponse(entity: InferredEntity, userId: UUID?): InferredEntityResponse {
        val voteStats = voteService.getVoteStats(
            VoteTargetType.INFERRED_ENTITY,
            entity.id!!,
            userId
        )

        val commentCount = commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(
            com.intrigsoft.ipitch.domain.CommentTargetType.INFERRED_ENTITY,
            entity.id!!
        )

        return InferredEntityResponse.from(entity, voteStats, commentCount)
    }
}
