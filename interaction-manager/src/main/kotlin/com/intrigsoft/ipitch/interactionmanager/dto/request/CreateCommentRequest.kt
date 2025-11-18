package com.intrigsoft.ipitch.interactionmanager.dto.request

import com.intrigsoft.ipitch.domain.CommentTargetType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.*

data class CreateCommentRequest(
    // UserId will be set from JWT, but included here for flexibility
    val userId: String = "",

    @field:NotBlank(message = "Content is required")
    @field:Size(min = 1, max = 10000, message = "Content must be between 1 and 10000 characters")
    val content: String,

    val parentCommentId: UUID? = null,

    @field:NotNull(message = "Target type is required")
    val targetType: CommentTargetType,

    @field:NotNull(message = "Target ID is required")
    val targetId: UUID
)
