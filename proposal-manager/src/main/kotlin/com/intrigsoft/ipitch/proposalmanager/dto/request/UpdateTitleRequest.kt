package com.intrigsoft.ipitch.proposalmanager.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.*

data class UpdateTitleRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,

    @field:NotNull(message = "Contributor ID is required")
    val contributorId: UUID,

    @field:NotBlank(message = "Commit message is required")
    val commitMessage: String
)
