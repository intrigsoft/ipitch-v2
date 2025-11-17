package com.intrigsoft.ipitch.interactionmanager.dto.response

import com.intrigsoft.ipitch.domain.Vote
import com.intrigsoft.ipitch.domain.VoteTargetType
import com.intrigsoft.ipitch.domain.VoteType
import java.time.LocalDateTime
import java.util.*

data class VoteResponse(
    val id: UUID,
    val userId: UUID,
    val targetType: VoteTargetType,
    val targetId: UUID,
    val voteType: VoteType,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(vote: Vote): VoteResponse {
            return VoteResponse(
                id = vote.id!!,
                userId = vote.userId,
                targetType = vote.targetType,
                targetId = vote.targetId,
                voteType = vote.voteType,
                createdAt = vote.createdAt,
                updatedAt = vote.updatedAt
            )
        }
    }
}
