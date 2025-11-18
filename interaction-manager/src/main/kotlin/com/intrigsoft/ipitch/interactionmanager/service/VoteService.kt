package com.intrigsoft.ipitch.interactionmanager.service

import com.intrigsoft.ipitch.domain.Vote
import com.intrigsoft.ipitch.domain.VoteTargetType
import com.intrigsoft.ipitch.domain.VoteType
import com.intrigsoft.ipitch.interactionmanager.dto.request.CreateVoteRequest
import com.intrigsoft.ipitch.interactionmanager.dto.response.VoteResponse
import com.intrigsoft.ipitch.interactionmanager.dto.response.VoteStatsResponse
import com.intrigsoft.ipitch.interactionmanager.exception.DuplicateVoteException
import com.intrigsoft.ipitch.interactionmanager.exception.UnauthorizedOperationException
import com.intrigsoft.ipitch.interactionmanager.exception.VoteNotFoundException
import com.intrigsoft.ipitch.repository.VoteRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

private val logger = KotlinLogging.logger {}

@Service
class VoteService(
    private val voteRepository: VoteRepository
) {

    @Transactional
    fun createOrUpdateVote(request: CreateVoteRequest): VoteResponse {
        logger.info { "Creating/updating vote for ${request.targetType}:${request.targetId} by user ${request.userId}" }

        // Check if user has already voted
        val existingVote = voteRepository.findByUserIdAndTargetTypeAndTargetId(
            request.userId,
            request.targetType,
            request.targetId
        )

        val vote = if (existingVote != null) {
            // Update existing vote
            logger.info { "Updating existing vote ${existingVote.id}" }
            existingVote.voteType = request.voteType
            existingVote.updatedAt = LocalDateTime.now()
            existingVote
        } else {
            // Create new vote
            logger.info { "Creating new vote" }
            Vote(
                userId = request.userId,
                targetType = request.targetType,
                targetId = request.targetId,
                voteType = request.voteType
            )
        }

        val savedVote = voteRepository.save(vote)
        logger.info { "Vote saved with id: ${savedVote.id}" }

        return VoteResponse.from(savedVote)
    }

    @Transactional
    fun deleteVote(voteId: UUID, userId: String): VoteResponse {
        logger.info { "Deleting vote $voteId by user $userId" }

        val vote = voteRepository.findById(voteId).orElseThrow {
            throw VoteNotFoundException(voteId)
        }

        // Check authorization
        if (vote.userId != userId) {
            throw UnauthorizedOperationException("You are not authorized to delete this vote")
        }

        voteRepository.delete(vote)
        logger.info { "Vote deleted: $voteId" }

        return VoteResponse.from(vote)
    }

    @Transactional
    fun removeVote(userId: String, targetType: VoteTargetType, targetId: UUID) {
        logger.info { "Removing vote for $targetType:$targetId by user $userId" }

        voteRepository.deleteByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId)
        logger.info { "Vote removed for $targetType:$targetId by user $userId" }
    }

    @Transactional(readOnly = true)
    fun getVote(voteId: UUID): VoteResponse {
        logger.debug { "Fetching vote $voteId" }

        val vote = voteRepository.findById(voteId).orElseThrow {
            throw VoteNotFoundException(voteId)
        }

        return VoteResponse.from(vote)
    }

    @Transactional(readOnly = true)
    fun getUserVote(userId: String, targetType: VoteTargetType, targetId: UUID): VoteResponse? {
        logger.debug { "Fetching user vote for $targetType:$targetId by user $userId" }

        val vote = voteRepository.findByUserIdAndTargetTypeAndTargetId(
            userId,
            targetType,
            targetId
        ) ?: return null

        return VoteResponse.from(vote)
    }

    @Transactional(readOnly = true)
    fun getVoteStats(targetType: VoteTargetType, targetId: UUID, userId: String?): VoteStatsResponse {
        logger.debug { "Fetching vote stats for $targetType:$targetId" }

        val upvotes = voteRepository.countUpvotes(targetType, targetId)
        val downvotes = voteRepository.countDownvotes(targetType, targetId)
        val score = voteRepository.getVoteScore(targetType, targetId) ?: 0L

        val userVote = userId?.let {
            voteRepository.findByUserIdAndTargetTypeAndTargetId(it, targetType, targetId)?.voteType?.name
        }

        return VoteStatsResponse(
            upvotes = upvotes,
            downvotes = downvotes,
            score = score,
            userVote = userVote
        )
    }

    @Transactional(readOnly = true)
    fun getVotesByTarget(targetType: VoteTargetType, targetId: UUID): List<VoteResponse> {
        logger.debug { "Fetching votes for $targetType:$targetId" }

        val votes = voteRepository.findByTargetTypeAndTargetId(targetType, targetId)

        return votes.map { VoteResponse.from(it) }
    }
}
