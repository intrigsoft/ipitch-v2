package com.intrigsoft.ipitch.interactionmanager.dto.request

import com.intrigsoft.ipitch.domain.VoteTargetType
import com.intrigsoft.ipitch.domain.VoteType
import jakarta.validation.constraints.NotNull
import java.util.*

data class CreateVoteRequest(
    @field:NotNull(message = "User ID is required")
    val userId: UUID,

    @field:NotNull(message = "Target type is required")
    val targetType: VoteTargetType,

    @field:NotNull(message = "Target ID is required")
    val targetId: UUID,

    @field:NotNull(message = "Vote type is required")
    val voteType: VoteType
)
