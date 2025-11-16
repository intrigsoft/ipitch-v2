package com.example.ipitch.proposalmanager.dto.response

import com.example.ipitch.proposalmanager.domain.ContributorStatus
import java.util.*

data class ContributorResponse(
    val id: UUID,
    val userId: UUID,
    val role: String,
    val status: ContributorStatus
)
