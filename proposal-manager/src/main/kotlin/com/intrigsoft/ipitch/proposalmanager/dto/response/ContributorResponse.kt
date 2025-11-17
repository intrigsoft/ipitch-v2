package com.intrigsoft.ipitch.proposalmanager.dto.response

import com.intrigsoft.ipitch.domain.ContributorStatus
import java.util.*

data class ContributorResponse(
    val id: UUID,
    val userId: UUID,
    val role: String,
    val status: ContributorStatus
)
