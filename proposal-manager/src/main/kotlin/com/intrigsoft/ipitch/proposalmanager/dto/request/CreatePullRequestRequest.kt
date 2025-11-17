package com.intrigsoft.ipitch.proposalmanager.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.*

data class CreatePullRequestRequest(
    @field:NotNull(message = "Contributor ID is required")
    val contributorId: UUID,

    @field:NotBlank(message = "Description is required")
    val description: String
)
