package com.intrigsoft.ipitch.proposalmanager.dto.response

import com.intrigsoft.ipitch.domain.ContributorStatus
import java.util.*

data class ContributorResponse(
    val id: UUID,
    val userId: String, // Keycloak user ID
    val role: String,
    val status: ContributorStatus
)
