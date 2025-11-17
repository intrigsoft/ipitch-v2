package com.intrigsoft.ipitch.proposalmanager.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.*

data class UpdateContentRequest(
    @field:NotBlank(message = "Content is required")
    val content: String,

    @field:NotNull(message = "Contributor ID is required")
    val contributorId: UUID,

    @field:NotBlank(message = "Commit message is required")
    val commitMessage: String
)
