package com.intrigsoft.ipitch.repository

import com.intrigsoft.ipitch.domain.InferredEntity
import com.intrigsoft.ipitch.domain.InferredEntityStatus
import com.intrigsoft.ipitch.domain.InferredEntityType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface InferredEntityRepository : JpaRepository<InferredEntity, UUID> {
    fun findByProposalId(proposalId: UUID): List<InferredEntity>

    fun findByProposalIdAndEntityType(
        proposalId: UUID,
        entityType: InferredEntityType
    ): List<InferredEntity>

    fun findByProposalIdAndStatus(
        proposalId: UUID,
        status: InferredEntityStatus
    ): List<InferredEntity>

    fun findBySourceCommentId(sourceCommentId: UUID): List<InferredEntity>

    fun findByEntityTypeAndStatus(
        entityType: InferredEntityType,
        status: InferredEntityStatus
    ): List<InferredEntity>

    @Query("""
        SELECT i FROM InferredEntity i
        WHERE i.proposalId = :proposalId
        AND i.entityType = :entityType
        AND i.status = :status
        ORDER BY i.confidenceScore DESC
    """)
    fun findByProposalIdAndEntityTypeAndStatusOrderByConfidenceDesc(
        proposalId: UUID,
        entityType: InferredEntityType,
        status: InferredEntityStatus
    ): List<InferredEntity>

    @Query("""
        SELECT i FROM InferredEntity i
        WHERE i.proposalId = :proposalId
        AND i.confidenceScore >= :minConfidence
        ORDER BY i.confidenceScore DESC
    """)
    fun findByProposalIdWithMinConfidence(
        proposalId: UUID,
        minConfidence: Double
    ): List<InferredEntity>

    fun countByProposalIdAndEntityType(
        proposalId: UUID,
        entityType: InferredEntityType
    ): Long
}
