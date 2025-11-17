package com.intrigsoft.ipitch.proposalviewmanager.dto

import com.intrigsoft.ipitch.domain.ProposalStatus

/**
 * DTO for searching proposals
 */
data class ProposalSearchRequest(
    val query: String? = null,
    val ownerId: String? = null,
    val status: ProposalStatus? = null,
    val fromDate: String? = null,
    val toDate: String? = null,
    val page: Int = 0,
    val size: Int = 20,
    val sortBy: String = "updatedAt",
    val sortOrder: String = "desc"
)
