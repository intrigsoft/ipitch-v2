package com.intrigsoft.ipitch.proposalmanager.dto.request

import com.intrigsoft.ipitch.domain.ProposalStatus

data class UpdateProposalMetadataRequest(
    val status: ProposalStatus?,
    val stats: Map<String, Any>?
)
