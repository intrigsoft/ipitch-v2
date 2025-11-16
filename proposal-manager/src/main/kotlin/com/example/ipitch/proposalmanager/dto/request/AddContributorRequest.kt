package com.example.ipitch.proposalmanager.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.*

data class AddContributorRequest(
    @field:NotNull(message = "User ID is required")
    val userId: UUID,

    @field:NotBlank(message = "Role is required")
    val role: String
)
