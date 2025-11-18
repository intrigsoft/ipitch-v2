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
    private val commentAnalysisRepository: CommentAnalysisElasticsearchRepository,
    private val aiServiceFactory: AIServiceFactory
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

    /**
     * Calculate comprehensive user scores using AI analysis
     * This combines comment analysis, proposal analysis, and commit data
     * Returns a map of scores suitable for storing in the User.scores field
     */
    suspend fun calculateUserScores(
        userId: String,
        comments: List<CommentSummary>,
        commits: List<CommitSummary>
    ): Map<String, Any> {
        logger.info { "Calculating AI-based user scores for user $userId" }

        try {
            // Get existing analytics
            val proposalMetrics = getUserProposalMetrics(UUID.fromString(userId))
            val commentMetrics = getUserCommentMetrics(UUID.fromString(userId))

            // Build context for AI
            val context = buildUserScoreContext(userId, comments, commits, proposalMetrics, commentMetrics)

            // Call AI to calculate scores
            val aiService = aiServiceFactory.getAIService()
            val systemPrompt = """
You are an expert in evaluating user contributions to a civic engagement platform.
You analyze user comments and code contributions to proposals and calculate scores.
Return ONLY a valid JSON object with the following structure:
{
  "overallQuality": <0-10>,
  "contributionScore": <0-10>,
  "sectorExpertise": {
    "IT": <0-10>,
    "Legal": <0-10>,
    // ... other sectors
  },
  "engagementLevel": <0-10>,
  "collaborationScore": <0-10>,
  "consistency": <0-10>
}
            """.trimIndent()

            val prompt = """
Analyze the following user activity and calculate comprehensive scores:

USER ID: $userId

COMMENTS (${comments.size} total):
${comments.take(20).joinToString("\n") { "- ${it.content.take(200)}" }}

COMMITS (${commits.size} total):
${commits.take(20).joinToString("\n") { "- ${it.message}" }}

PROPOSAL METRICS:
- Total proposals: ${proposalMetrics.totalProposals}
- Average clarity score: ${proposalMetrics.averageClarityScore}
- High quality proposals: ${proposalMetrics.highQualityProposalCount}
- Sector expertise: ${proposalMetrics.sectorExpertise}

COMMENT METRICS:
- Total comments: ${commentMetrics.totalComments}
- Flagged comments: ${commentMetrics.flaggedCommentCount} (${commentMetrics.flaggedCommentPercentage}%)
- Average relevance: ${commentMetrics.averageRelevanceScore}
- Mode distribution: ${commentMetrics.modeDistribution}

Calculate comprehensive scores (0-10) for this user based on:
1. Overall quality of contributions
2. Contribution score (quantity and consistency)
3. Sector expertise (which sectors they contribute to most effectively)
4. Engagement level (how active they are)
5. Collaboration score (how well they work with others)
6. Consistency (how consistent their quality is over time)

Return ONLY valid JSON.
            """.trimIndent()

            val response = aiService.generateStructuredResponse(prompt, systemPrompt)

            // Parse AI response
            val scores = parseAIScoresResponse(response)

            logger.info { "Successfully calculated user scores for $userId" }
            return scores
        } catch (e: Exception) {
            logger.error(e) { "Error calculating user scores for $userId" }

            // Return default scores on error
            return mapOf(
                "overallQuality" to 5.0,
                "contributionScore" to 5.0,
                "engagementLevel" to 5.0,
                "collaborationScore" to 5.0,
                "consistency" to 5.0,
                "error" to "Failed to calculate scores: ${e.message}"
            )
        }
    }

    /**
     * Builds context string for AI analysis
     */
    private fun buildUserScoreContext(
        userId: String,
        comments: List<CommentSummary>,
        commits: List<CommitSummary>,
        proposalMetrics: UserProposalMetrics,
        commentMetrics: UserCommentMetrics
    ): String {
        return """
User Activity Summary:
- User ID: $userId
- Total Comments: ${comments.size}
- Total Commits: ${commits.size}
- Proposals Created: ${proposalMetrics.totalProposals}
- Average Proposal Quality: ${proposalMetrics.averageClarityScore}/10
- Flagged Comment Rate: ${commentMetrics.flaggedCommentPercentage}%
- Comment Relevance: ${commentMetrics.averageRelevanceScore}/10
        """.trimIndent()
    }

    /**
     * Parses AI response to extract scores
     */
    private fun parseAIScoresResponse(response: String): Map<String, Any> {
        try {
            // Simple JSON parsing for the response
            val scores = mutableMapOf<String, Any>()

            // Extract numeric scores using regex
            val overallQuality = extractScore(response, "overallQuality")
            val contributionScore = extractScore(response, "contributionScore")
            val engagementLevel = extractScore(response, "engagementLevel")
            val collaborationScore = extractScore(response, "collaborationScore")
            val consistency = extractScore(response, "consistency")

            scores["overallQuality"] = overallQuality
            scores["contributionScore"] = contributionScore
            scores["engagementLevel"] = engagementLevel
            scores["collaborationScore"] = collaborationScore
            scores["consistency"] = consistency

            // Extract sector expertise
            val sectorExpertise = extractSectorScores(response)
            if (sectorExpertise.isNotEmpty()) {
                scores["sectorExpertise"] = sectorExpertise
            }

            return scores
        } catch (e: Exception) {
            logger.error(e) { "Error parsing AI scores response" }
            return mapOf(
                "overallQuality" to 5.0,
                "error" to "Failed to parse AI response"
            )
        }
    }

    /**
     * Extracts a numeric score from AI response
     */
    private fun extractScore(response: String, fieldName: String): Double {
        val pattern = """"$fieldName":\s*(\d+\.?\d*)""".toRegex()
        val match = pattern.find(response)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 5.0
    }

    /**
     * Extracts sector scores from AI response
     */
    private fun extractSectorScores(response: String): Map<String, Double> {
        val sectorScores = mutableMapOf<String, Double>()
        val sectorPattern = """"([A-Za-z]+)":\s*(\d+\.?\d*)""".toRegex()

        // Find the sectorExpertise section
        val sectorSection = response.substringAfter("sectorExpertise", "").substringBefore("}", "")

        sectorPattern.findAll(sectorSection).forEach { match ->
            val sector = match.groupValues[1]
            val score = match.groupValues[2].toDoubleOrNull() ?: 0.0
            if (score > 0) {
                sectorScores[sector] = score
            }
        }

        return sectorScores
    }
}

/**
 * Summary of a comment for scoring
 */
data class CommentSummary(
    val id: String,
    val content: String,
    val createdAt: String
)

/**
 * Summary of a commit for scoring
 */
data class CommitSummary(
    val hash: String,
    val message: String,
    val timestamp: String,
    val proposalId: String?
)

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
