package com.intrigsoft.ipitch.proposalviewmanager.dto

import com.intrigsoft.ipitch.domain.ProposalStatus
import java.time.LocalDateTime
import java.util.*

/**
 * DTO for publishing a proposal to the view manager
 * This is used by the proposal-manager to send proposal data to be indexed
 */
data class ProposalPublishDto(
    val id: UUID,
    val title: String,
    val content: String,
    val ownerId: String, // Keycloak user ID
    val ownerName: String? = null,
    val contributors: List<ContributorDto> = emptyList(),
    val version: String,
    val status: ProposalStatus,
    val stats: Map<String, Any> = emptyMap(),
    val workingBranch: String? = null,
    val gitCommitHash: String? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    data class ContributorDto(
        val id: UUID,
        val userId: String, // Keycloak user ID
        val userName: String? = null,
        val role: String,
        val status: String
    )
}
