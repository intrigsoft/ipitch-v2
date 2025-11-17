package com.intrigsoft.ipitch.interactionmanager.dto.request

import com.intrigsoft.ipitch.domain.InferredEntityStatus
import jakarta.validation.constraints.NotNull
import java.util.*

data class UpdateInferredEntityStatusRequest(
    @field:NotNull(message = "Status is required")
    val status: InferredEntityStatus,

    @field:NotNull(message = "Reviewer ID is required")
    val reviewerId: UUID
)
