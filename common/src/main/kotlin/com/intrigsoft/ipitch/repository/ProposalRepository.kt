package com.intrigsoft.ipitch.repository

import com.intrigsoft.ipitch.domain.Proposal
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ProposalRepository : JpaRepository<Proposal, UUID> {
    fun findByOwnerId(ownerId: UUID): List<Proposal>
}
