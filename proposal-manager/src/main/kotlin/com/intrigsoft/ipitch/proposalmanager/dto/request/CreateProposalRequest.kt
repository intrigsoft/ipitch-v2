package com.intrigsoft.ipitch.proposalmanager.dto.request

import jakarta.validation.constraints.NotBlank

data class CreateProposalRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,

    @field:NotBlank(message = "Content is required")
    val content: String,

    // OwnerId will be set from JWT, but included here for flexibility
    val ownerId: String = ""
)
