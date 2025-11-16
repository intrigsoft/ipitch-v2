package com.example.ipitch.proposalmanager.repository

import com.example.ipitch.proposalmanager.domain.Contributor
import com.example.ipitch.proposalmanager.domain.Proposal
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ContributorRepository : JpaRepository<Contributor, UUID> {
    fun findByProposalAndUserId(proposal: Proposal, userId: UUID): Contributor?
    fun findByProposal(proposal: Proposal): List<Contributor>
}
