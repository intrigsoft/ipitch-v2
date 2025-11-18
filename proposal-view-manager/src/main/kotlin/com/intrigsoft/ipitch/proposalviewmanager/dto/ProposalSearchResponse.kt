package com.intrigsoft.ipitch.proposalviewmanager.dto

import com.intrigsoft.ipitch.domain.ProposalStatus
import java.time.LocalDateTime
import java.util.*

/**
 * DTO for proposal search results
 */
data class ProposalSearchResponse(
    val id: UUID,
    val title: String,
    val content: String,
    val ownerId: String, // Keycloak user ID
    val ownerName: String? = null,
    val contributors: List<ContributorDto> = emptyList(),
    val version: String,
    val status: ProposalStatus,
    val stats: Map<String, Any> = emptyMap(),
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

data class PagedProposalSearchResponse(
    val content: List<ProposalSearchResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int
)
