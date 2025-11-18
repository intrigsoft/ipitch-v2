package com.intrigsoft.ipitch.aiintegration.repository

import com.intrigsoft.ipitch.domain.ProposalConcern
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ProposalConcernRepository : JpaRepository<ProposalConcern, UUID> {

    fun findByProposalId(proposalId: UUID): List<ProposalConcern>

    @Query("""
        SELECT c FROM ProposalConcern c
        LEFT JOIN FETCH c.comments
        WHERE c.proposalId = :proposalId
    """)
    fun findByProposalIdWithComments(proposalId: UUID): List<ProposalConcern>

    fun findByEmbeddingId(embeddingId: UUID): ProposalConcern?
}
