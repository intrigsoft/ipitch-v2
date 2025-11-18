package com.intrigsoft.ipitch.usermanager.service

import com.intrigsoft.ipitch.domain.Comment
import com.intrigsoft.ipitch.repository.CommentRepository
import com.intrigsoft.ipitch.repository.UserRepository
import com.intrigsoft.ipitch.aiintegration.service.UserAnalyticsService
import com.intrigsoft.ipitch.aiintegration.service.CommentSummary
import com.intrigsoft.ipitch.aiintegration.service.CommitSummary
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.RestClientException
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Service
class UserScoreService(
    private val userRepository: UserRepository,
    private val commentRepository: CommentRepository,
    private val userAnalyticsService: UserAnalyticsService,
    @Value("\${proposal-manager.url:http://localhost:8082}") private val proposalManagerUrl: String
) {

    private val restTemplate = RestTemplate()

    /**
     * Checks if user is dirty and recalculates scores if needed
     * This should be called when viewing a user profile
     */
    fun recalculateScoresIfDirty(userId: String) {
        logger.info { "Checking if user $userId needs score recalculation" }

        val user = userRepository.findById(userId).orElse(null) ?: run {
            logger.warn { "User not found: $userId" }
            return
        }

        if (!user.dirty) {
            logger.debug { "User $userId is not dirty, skipping score recalculation" }
            return
        }

        logger.info { "User $userId is dirty, triggering score recalculation" }

        try {
            // Fetch user comments
            val comments = fetchUserComments(userId)
            logger.info { "Found ${comments.size} comments for user $userId" }

            // Fetch user commits from proposal-manager API
            val commits = fetchUserCommits(userId)
            logger.info { "Found ${commits.size} commits for user $userId" }

            // Calculate scores using AI
            val scores = runBlocking {
                userAnalyticsService.calculateUserScores(userId, comments, commits)
            }

            logger.info { "Calculated scores for user $userId: $scores" }

            // Update user with new scores and mark as clean
            val updatedUser = user.copy(
                scores = scores,
                dirty = false,
                updatedAt = LocalDateTime.now()
            )
            userRepository.save(updatedUser)

            logger.info { "Successfully updated scores for user $userId and marked as clean" }
        } catch (e: Exception) {
            logger.error(e) { "Error recalculating scores for user $userId" }
            // Don't throw - we don't want profile view to fail if score calculation fails
        }
    }

    /**
     * Fetches all comments for a user from the database
     */
    private fun fetchUserComments(userId: String): List<CommentSummary> {
        return try {
            commentRepository.findByUserIdAndDeletedFalse(userId).map { comment ->
                CommentSummary(
                    id = comment.id.toString(),
                    content = comment.content,
                    createdAt = comment.createdAt.toString()
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Error fetching comments for user $userId" }
            emptyList()
        }
    }

    /**
     * Fetches all commits for a user from proposal-manager API
     */
    private fun fetchUserCommits(userId: String): List<CommitSummary> {
        return try {
            val url = "$proposalManagerUrl/api/proposals/commits/user/$userId"
            logger.debug { "Fetching commits from: $url" }

            val response = restTemplate.getForObject(url, UserCommitsResponse::class.java)

            response?.data?.map { commit ->
                CommitSummary(
                    hash = commit.commitHash,
                    message = commit.message,
                    timestamp = commit.timestamp,
                    proposalId = commit.proposalId
                )
            } ?: emptyList()
        } catch (e: RestClientException) {
            logger.error(e) { "Error fetching commits for user $userId from proposal-manager" }
            emptyList()
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error fetching commits for user $userId" }
            emptyList()
        }
    }
}

/**
 * Response wrapper for user commits API
 */
data class UserCommitsResponse(
    val success: Boolean,
    val message: String,
    val data: List<CommitInfo>
)

/**
 * Commit information from proposal-manager API
 */
data class CommitInfo(
    val commitHash: String,
    val message: String,
    val authorName: String,
    val authorEmail: String,
    val timestamp: String,
    val proposalId: String?
)
