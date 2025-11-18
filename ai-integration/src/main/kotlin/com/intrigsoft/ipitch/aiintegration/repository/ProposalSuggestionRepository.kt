package com.intrigsoft.ipitch.aiintegration.repository

import com.intrigsoft.ipitch.domain.ProposalSuggestion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ProposalSuggestionRepository : JpaRepository<ProposalSuggestion, UUID> {

    fun findByProposalId(proposalId: UUID): List<ProposalSuggestion>

    @Query("""
        SELECT s FROM ProposalSuggestion s
        LEFT JOIN FETCH s.comments
        WHERE s.proposalId = :proposalId
    """)
    fun findByProposalIdWithComments(proposalId: UUID): List<ProposalSuggestion>

    fun findByEmbeddingId(embeddingId: UUID): ProposalSuggestion?
}
