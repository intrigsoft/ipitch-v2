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
    val ownerId: UUID,
    val ownerName: String? = null,
    val contributors: List<ContributorDto> = emptyList(),
    val version: String,
    val status: ProposalStatus,
    val stats: Map<String, Any> = emptyMap(),
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class PagedProposalSearchResponse(
    val content: List<ProposalSearchResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int
)
