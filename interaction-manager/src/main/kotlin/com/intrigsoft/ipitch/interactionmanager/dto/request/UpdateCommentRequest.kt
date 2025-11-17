package com.intrigsoft.ipitch.interactionmanager.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateCommentRequest(
    @field:NotBlank(message = "Content is required")
    @field:Size(min = 1, max = 10000, message = "Content must be between 1 and 10000 characters")
    val content: String
)
