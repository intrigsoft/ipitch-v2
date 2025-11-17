package com.intrigsoft.ipitch.aiintegration.repository

import com.intrigsoft.ipitch.aiintegration.model.ProposalAnalysis
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Repository for proposal analysis results
 */
@Repository
interface ProposalAnalysisRepository : JpaRepository<ProposalAnalysis, UUID> {

    /**
     * Find analysis for a specific proposal
     */
    fun findByProposalId(proposalId: UUID): ProposalAnalysis?

    /**
     * Check if analysis exists for a proposal
     */
    fun existsByProposalId(proposalId: UUID): Boolean

    /**
     * Delete analysis for a proposal
     */
    fun deleteByProposalId(proposalId: UUID)
}
