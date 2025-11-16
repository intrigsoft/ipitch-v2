package com.example.ipitch.proposalmanager.dto.request

import com.example.ipitch.proposalmanager.domain.ProposalStatus

data class UpdateProposalMetadataRequest(
    val status: ProposalStatus?,
    val stats: Map<String, Any>?
)
