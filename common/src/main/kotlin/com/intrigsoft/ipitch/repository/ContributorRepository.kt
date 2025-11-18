package com.intrigsoft.ipitch.repository

import com.intrigsoft.ipitch.domain.Contributor
import com.intrigsoft.ipitch.domain.Proposal
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ContributorRepository : JpaRepository<Contributor, UUID> {
    fun findByProposalAndUserId(proposal: Proposal, userId: String): Contributor?
    fun findByProposal(proposal: Proposal): List<Contributor>
}
