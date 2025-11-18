package com.intrigsoft.ipitch.proposalmanager.dto.response

import com.intrigsoft.ipitch.domain.ProposalStatus
import java.time.LocalDateTime
import java.util.*

data class ProposalResponse(
    val id: UUID,
    val title: String,
    val content: String,
    val ownerId: String, // Keycloak user ID
    val contributors: List<ContributorResponse>,
    val version: String,
    val status: ProposalStatus,
    val stats: Map<String, Any>,
    val workingBranch: String?,
    val gitCommitHash: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
