package com.example.ipitch.proposalmanager.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.*

data class CreateProposalRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,

    @field:NotBlank(message = "Content is required")
    val content: String,

    @field:NotNull(message = "Owner ID is required")
    val ownerId: UUID
)
