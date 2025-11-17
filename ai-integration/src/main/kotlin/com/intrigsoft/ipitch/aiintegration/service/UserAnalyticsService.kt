package com.intrigsoft.ipitch.aiintegration.service

import com.intrigsoft.ipitch.aiintegration.elasticsearch.CommentAnalysisElasticsearchRepository
import com.intrigsoft.ipitch.aiintegration.elasticsearch.ProposalAnalysisElasticsearchRepository
import mu.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Service for user behavior analytics based on AI analysis results
 * Provides metrics for user quality, aggressiveness, sector expertise, etc.
 */
@Service
class UserAnalyticsService(
    private val proposalAnalysisRepository: ProposalAnalysisElasticsearchRepository,
    private val commentAnalysisRepository: CommentAnalysisElasticsearchRepository
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Get user proposal quality metrics
     */
    fun getUserProposalMetrics(userId: UUID): UserProposalMetrics {
        val userIdStr = userId.toString()

        val allProposals = proposalAnalysisRepository.findByOwnerId(
            userIdStr,
            PageRequest.of(0, 1000)  // Fetch up to 1000 proposals
        ).content

        if (allProposals.isEmpty()) {
            return UserProposalMetrics(
                userId = userId,
                totalProposals = 0,
                averageClarityScore = 0.0,
                highQualityProposalCount = 0,
                sectorExpertise = emptyMap()
            )
        }

        val avgClarity = allProposals.map { it.clarityScore }.average()
        val highQuality = allProposals.count { it.clarityScore >= 7.5 }

        // Calculate sector expertise (average score per sector)
        val sectorScores = mutableMapOf<String, MutableList<Double>>()
        allProposals.forEach { proposal ->
            proposal.sectorScores.forEach { score ->
                sectorScores.computeIfAbsent(score.sector) { mutableListOf() }.add(score.score)
            }
        }

        val sectorExpertise = sectorScores.mapValues { (_, scores) ->
            scores.average()
        }.filter { it.value >= 5.0 }  // Only include sectors with average score >= 5

        return UserProposalMetrics(
            userId = userId,
            totalProposals = allProposals.size,
            averageClarityScore = avgClarity,
            highQualityProposalCount = highQuality,
            sectorExpertise = sectorExpertise
        )
    }

    /**
     * Get user comment behavior metrics
     */
    fun getUserCommentMetrics(userId: UUID): UserCommentMetrics {
        val userIdStr = userId.toString()

        val allComments = commentAnalysisRepository.findByUserId(
            userIdStr,
            PageRequest.of(0, 1000)  // Fetch up to 1000 comments
        ).content

        if (allComments.isEmpty()) {
            return UserCommentMetrics(
                userId = userId,
                totalComments = 0,
                flaggedCommentCount = 0,
                flaggedCommentPercentage = 0.0,
                averageRelevanceScore = 0.0,
                modeDistribution = emptyMap(),
                marketingCommentCount = 0,
                sectorContributions = emptyMap()
            )
        }

        val flaggedCount = allComments.count { it.isFlagged }
        val flaggedPercentage = (flaggedCount.toDouble() / allComments.size) * 100

        // Average relevance (only for non-flagged comments)
        val nonFlagged = allComments.filter { !it.isFlagged }
        val avgRelevance = nonFlagged.mapNotNull { it.relevanceScore }.average()

        // Mode distribution (aggressiveness indicator)
        val modeDistribution = allComments
            .filter { !it.isFlagged && it.mode != null }
            .groupBy { it.mode!! }
            .mapValues { (_, comments) -> comments.size }

        val marketingCount = allComments.count { it.isMarketing }

        // Sector contributions (which sectors user comments on most)
        val sectorScores = mutableMapOf<String, MutableList<Double>>()
        allComments.filter { !it.isFlagged }.forEach { comment ->
            comment.sectorScores?.forEach { score ->
                sectorScores.computeIfAbsent(score.sector) { mutableListOf() }.add(score.score)
            }
        }

        val sectorContributions = sectorScores.mapValues { (_, scores) ->
            SectorContribution(
                commentCount = scores.size,
                averageScore = scores.average()
            )
        }.filter { it.value.averageScore >= 3.0 }  // Only sectors with meaningful contribution

        return UserCommentMetrics(
            userId = userId,
            totalComments = allComments.size,
            flaggedCommentCount = flaggedCount,
            flaggedCommentPercentage = flaggedPercentage,
            averageRelevanceScore = avgRelevance,
            modeDistribution = modeDistribution,
            marketingCommentCount = marketingCount,
            sectorContributions = sectorContributions
        )
    }

    /**
     * Get comprehensive user analytics profile
     */
    fun getUserProfile(userId: UUID): UserAnalyticsProfile {
        logger.info { "Generating analytics profile for user $userId" }

        val proposalMetrics = getUserProposalMetrics(userId)
        val commentMetrics = getUserCommentMetrics(userId)

        // Calculate aggressiveness score (0-10, higher = more aggressive/critical)
        val aggressiveness = calculateAggressiveness(commentMetrics)

        // Calculate overall quality score (0-10)
        val quality = calculateQualityScore(proposalMetrics, commentMetrics)

        // Determine user lenience based on voting and comment patterns
        val lenience = calculateLenience(commentMetrics)

        return UserAnalyticsProfile(
            userId = userId,
            proposalMetrics = proposalMetrics,
            commentMetrics = commentMetrics,
            aggressivenessScore = aggressiveness,
            qualityScore = quality,
            lenienceScore = lenience
        )
    }

    /**
     * Calculate aggressiveness score based on comment modes
     */
    private fun calculateAggressiveness(metrics: UserCommentMetrics): Double {
        if (metrics.totalComments == 0) return 0.0

        val critical = metrics.modeDistribution["CRITICAL"] ?: 0
        val supportive = metrics.modeDistribution["SUPPORTIVE"] ?: 0
        val neutral = metrics.modeDistribution["NEUTRAL"] ?: 0
        val inquisitive = metrics.modeDistribution["INQUISITIVE"] ?: 0

        val total = critical + supportive + neutral + inquisitive
        if (total == 0) return 5.0  // Neutral if no mode data

        // Critical comments increase score, supportive decrease it
        val criticalRatio = critical.toDouble() / total
        val supportiveRatio = supportive.toDouble() / total

        // Scale: 0 (very supportive) to 10 (very critical)
        return ((criticalRatio - supportiveRatio) * 5) + 5
    }

    /**
     * Calculate overall quality score
     */
    private fun calculateQualityScore(
        proposalMetrics: UserProposalMetrics,
        commentMetrics: UserCommentMetrics
    ): Double {
        // Weight: 60% proposal quality, 40% comment quality
        val proposalQuality = proposalMetrics.averageClarityScore
        val commentQuality = if (commentMetrics.totalComments > 0) {
            // Quality decreases with flagged comments
            val flaggedPenalty = commentMetrics.flaggedCommentPercentage / 10.0
            val relevanceBonus = (commentMetrics.averageRelevanceScore ?: 5.0)
            ((relevanceBonus - flaggedPenalty) * 10.0) / 10.0
        } else {
            proposalQuality  // Use proposal quality if no comments
        }

        return (proposalQuality * 0.6) + (commentQuality * 0.4)
    }

    /**
     * Calculate lenience score (0-10, higher = more lenient/less critical)
     */
    private fun calculateLenience(metrics: UserCommentMetrics): Double {
        if (metrics.totalComments == 0) return 5.0  // Neutral if no data

        val supportive = metrics.modeDistribution["SUPPORTIVE"] ?: 0
        val critical = metrics.modeDistribution["CRITICAL"] ?: 0
        val neutral = metrics.modeDistribution["NEUTRAL"] ?: 0

        val total = supportive + critical + neutral
        if (total == 0) return 5.0

        val supportiveRatio = supportive.toDouble() / total
        val criticalRatio = critical.toDouble() / total

        // High lenience = more supportive, fewer critical comments
        return ((supportiveRatio - criticalRatio) * 5) + 5
    }

    /**
     * Get users with highest proposal quality
     */
    fun getTopQualityProposalAuthors(limit: Int = 10): List<String> {
        val highQualityProposals = proposalAnalysisRepository.findByClarityScoreBetween(
            8.0, 10.0,
            PageRequest.of(0, 1000)
        ).content

        return highQualityProposals
            .groupBy { it.ownerId }
            .mapValues { (_, proposals) -> proposals.size }
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    /**
     * Get users with most flagged comments (for moderation)
     */
    fun getUsersWithMostFlaggedComments(limit: Int = 10): List<Pair<String, Int>> {
        val flaggedComments = commentAnalysisRepository.findByIsFlagged(
            true,
            PageRequest.of(0, 1000)
        ).content

        return flaggedComments
            .groupBy { it.userId }
            .mapValues { (_, comments) -> comments.size }
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }
}

/**
 * User proposal quality metrics
 */
data class UserProposalMetrics(
    val userId: UUID,
    val totalProposals: Int,
    val averageClarityScore: Double,
    val highQualityProposalCount: Int,  // Proposals with clarity >= 7.5
    val sectorExpertise: Map<String, Double>  // Sector -> Average score
)

/**
 * User comment behavior metrics
 */
data class UserCommentMetrics(
    val userId: UUID,
    val totalComments: Int,
    val flaggedCommentCount: Int,
    val flaggedCommentPercentage: Double,
    val averageRelevanceScore: Double,
    val modeDistribution: Map<String, Int>,  // Mode -> Count
    val marketingCommentCount: Int,
    val sectorContributions: Map<String, SectorContribution>  // Sector -> Contribution
)

/**
 * Sector contribution details
 */
data class SectorContribution(
    val commentCount: Int,
    val averageScore: Double
)

/**
 * Comprehensive user analytics profile
 */
data class UserAnalyticsProfile(
    val userId: UUID,
    val proposalMetrics: UserProposalMetrics,
    val commentMetrics: UserCommentMetrics,
    val aggressivenessScore: Double,  // 0 (very supportive) to 10 (very critical/aggressive)
    val qualityScore: Double,  // 0 (low quality) to 10 (high quality)
    val lenienceScore: Double  // 0 (very critical) to 10 (very lenient)
)
