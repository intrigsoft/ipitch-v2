package com.intrigsoft.ipitch.aiintegration.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.intrigsoft.ipitch.aiintegration.config.AIProperties
import com.intrigsoft.ipitch.aiintegration.model.*
import com.intrigsoft.ipitch.aiintegration.repository.CommentAnalysisRepository
import com.intrigsoft.ipitch.domain.Comment
import com.intrigsoft.ipitch.domain.Proposal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Service for AI-powered comment analysis
 */
@Service
class CommentAnalysisService(
    private val aiServiceFactory: AIServiceFactory,
    private val commentAnalysisRepository: CommentAnalysisRepository,
    private val aiProperties: AIProperties,
    private val objectMapper: ObjectMapper
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Analyze a comment - performs governance check first
     * If flagged, returns immediately with flag info
     * Otherwise, performs full analysis
     */
    @Transactional
    suspend fun analyzeComment(
        comment: Comment,
        proposal: Proposal,
        commentThread: List<Comment> = emptyList()
    ): CommentAnalysisResult = withContext(Dispatchers.IO) {
        logger.info { "Analyzing comment: ${comment.id}" }

        val aiService = aiServiceFactory.getAIService()
        val provider = aiServiceFactory.getCurrentProvider()

        // Step 1: Governance check (always performed first)
        val governanceResult = performGovernanceCheck(comment.content)

        if (governanceResult.isFlagged) {
            logger.warn { "Comment ${comment.id} flagged for: ${governanceResult.flags}" }

            val result = CommentAnalysisResult(
                commentId = comment.id!!,
                governanceFlags = governanceResult.flags,
                governanceScore = governanceResult.score,
                isFlagged = true,
                flagReason = governanceResult.reason,
                analyzedAt = Instant.now(),
                model = getModelName(),
                provider = provider
            )

            saveAnalysisResult(result)
            return@withContext result
        }

        // Step 2: Full analysis (only if governance passes)
        logger.debug { "Governance check passed, performing full analysis" }

        val fullAnalysis = performFullAnalysis(comment, proposal, commentThread)

        val result = CommentAnalysisResult(
            commentId = comment.id,
            governanceFlags = governanceResult.flags,
            governanceScore = governanceResult.score,
            isFlagged = false,
            flagReason = null,
            relevanceScore = fullAnalysis.relevanceScore,
            sectorScores = fullAnalysis.sectorScores,
            mode = fullAnalysis.mode,
            isMarketing = fullAnalysis.isMarketing,
            marketingScore = fullAnalysis.marketingScore,
            analyzedAt = Instant.now(),
            model = getModelName(),
            provider = provider
        )

        saveAnalysisResult(result)
        logger.info { "Successfully analyzed comment: ${comment.id}" }

        result
    }

    /**
     * Perform governance/moderation check
     */
    private suspend fun performGovernanceCheck(content: String): GovernanceResult {
        val aiService = aiServiceFactory.getAIService()

        val systemPrompt = """
            You are a content moderation system. Analyze the text for harmful content including:
            - Hate speech
            - Harassment or bullying
            - Self-harm content
            - Sexual content
            - Violence or threats
            - Profanity (severe)
            - Spam or repetitive content

            Be fair and context-aware. Not all strong language is problematic.
        """.trimIndent()

        val prompt = """
            Analyze this comment for harmful content:

            "$content"

            Respond with JSON in this exact format:
            {
              "flags": ["HATE_SPEECH", "VIOLENCE"],
              "score": 0.85,
              "isFlagged": true,
              "reason": "Brief explanation of why it was flagged"
            }

            - flags: Array of applicable GovernanceFlag values (HATE_SPEECH, HARASSMENT, SELF_HARM, SEXUAL_CONTENT, VIOLENCE, PROFANITY, SPAM, MISINFORMATION, NONE)
            - score: 0.0 to 1.0 (higher = more problematic)
            - isFlagged: true if score >= ${aiProperties.moderation.autoFlagThreshold}
            - reason: Explanation if flagged, null otherwise
        """.trimIndent()

        val jsonResponse = aiService.generateStructuredResponse(
            prompt = prompt,
            systemPrompt = systemPrompt
        )

        return parseGovernanceResponse(jsonResponse)
    }

    /**
     * Perform full comment analysis (relevance, sectors, mode, marketing)
     */
    private suspend fun performFullAnalysis(
        comment: Comment,
        proposal: Proposal,
        commentThread: List<Comment>
    ): FullAnalysis {
        val aiService = aiServiceFactory.getAIService()

        // Build context with proposal and thread
        val context = buildAnalysisContext(comment, proposal, commentThread)

        val systemPrompt = """
            You are an expert analyst evaluating comments on proposals.
            Assess relevance, sector alignment, tone/mode, and promotional content.
        """.trimIndent()

        val sectors = aiProperties.sectors.enabled.joinToString(", ")

        val prompt = """
            Analyze this comment in the context of the proposal and conversation thread:

            $context

            Provide:
            1. Relevance score (0.0-10.0) to the proposal and thread
            2. Sector-specific scores (0.0-10.0) for applicable sectors
            3. Mode/tone of the comment (SUPPORTIVE, CRITICAL, NEUTRAL, INQUISITIVE, SUGGESTIVE)
            4. Marketing detection (is this promotional content?)

            Available sectors: $sectors

            Respond with JSON in this exact format:
            {
              "relevanceScore": 8.5,
              "sectorScores": [
                {"sector": "Healthcare", "score": 9.0}
              ],
              "mode": "SUPPORTIVE",
              "isMarketing": false,
              "marketingScore": 0.1
            }

            - relevanceScore: How relevant is this comment to the proposal/thread
            - sectorScores: Only include sectors relevant to the comment (score > 0)
            - mode: One of: SUPPORTIVE, CRITICAL, NEUTRAL, INQUISITIVE, SUGGESTIVE
            - isMarketing: true if marketingScore >= 0.7
            - marketingScore: 0.0-1.0 (higher = more promotional)
        """.trimIndent()

        val jsonResponse = aiService.generateStructuredResponse(
            prompt = prompt,
            systemPrompt = systemPrompt
        )

        return parseFullAnalysisResponse(jsonResponse)
    }

    /**
     * Build analysis context with proposal and comment thread
     */
    private fun buildAnalysisContext(
        comment: Comment,
        proposal: Proposal,
        commentThread: List<Comment>
    ): String {
        val sb = StringBuilder()

        sb.appendLine("PROPOSAL:")
        sb.appendLine("Title: ${proposal.title}")
        sb.appendLine("Content: ${proposal.content.take(1000)}...")
        sb.appendLine()

        if (commentThread.isNotEmpty()) {
            sb.appendLine("COMMENT THREAD:")
            commentThread.forEachIndexed { index, threadComment ->
                sb.appendLine("Comment ${index + 1}: ${threadComment.content}")
            }
            sb.appendLine()
        }

        sb.appendLine("NEW COMMENT TO ANALYZE:")
        sb.appendLine(comment.content)

        return sb.toString()
    }

    /**
     * Parse governance check response
     */
    private fun parseGovernanceResponse(jsonResponse: String): GovernanceResult {
        try {
            val jsonNode = objectMapper.readTree(jsonResponse)

            val flags = mutableListOf<GovernanceFlag>()
            jsonNode["flags"]?.forEach { node ->
                flags.add(GovernanceFlag.valueOf(node.asText()))
            }

            val score = jsonNode["score"].asDouble()
            val isFlagged = jsonNode["isFlagged"].asBoolean()
            val reason = jsonNode["reason"]?.asText()

            return GovernanceResult(flags, score, isFlagged, reason)
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse governance response: $jsonResponse" }
            // Default to safe mode - flag for manual review
            return GovernanceResult(
                flags = listOf(GovernanceFlag.NONE),
                score = 0.5,
                isFlagged = true,
                reason = "Failed to parse AI response - flagged for manual review"
            )
        }
    }

    /**
     * Parse full analysis response
     */
    private fun parseFullAnalysisResponse(jsonResponse: String): FullAnalysis {
        try {
            val jsonNode = objectMapper.readTree(jsonResponse)

            val relevanceScore = jsonNode["relevanceScore"].asDouble()

            val sectorScores = mutableListOf<SectorScore>()
            jsonNode["sectorScores"]?.forEach { node ->
                val sector = node["sector"].asText()
                val score = node["score"].asDouble()
                sectorScores.add(SectorScore(sector, score))
            }

            val mode = ContentMode.valueOf(jsonNode["mode"].asText())
            val isMarketing = jsonNode["isMarketing"].asBoolean()
            val marketingScore = jsonNode["marketingScore"].asDouble()

            return FullAnalysis(relevanceScore, sectorScores, mode, isMarketing, marketingScore)
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse full analysis response: $jsonResponse" }
            throw Exception("Failed to parse AI analysis response", e)
        }
    }

    /**
     * Save analysis result to database
     */
    private fun saveAnalysisResult(result: CommentAnalysisResult) {
        val entity = CommentAnalysis(
            commentId = result.commentId,
            governanceFlags = result.governanceFlags,
            governanceScore = result.governanceScore,
            isFlagged = result.isFlagged,
            flagReason = result.flagReason,
            relevanceScore = result.relevanceScore,
            sectorScores = result.sectorScores,
            mode = result.mode,
            isMarketing = result.isMarketing,
            marketingScore = result.marketingScore,
            model = result.model,
            provider = result.provider,
            analyzedAt = result.analyzedAt,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        commentAnalysisRepository.save(entity)
    }

    /**
     * Get existing analysis for a comment
     */
    @Transactional(readOnly = true)
    fun getCommentAnalysis(commentId: UUID): CommentAnalysisResult? {
        return commentAnalysisRepository.findByCommentId(commentId)?.let {
            CommentAnalysisResult(
                commentId = it.commentId,
                governanceFlags = it.governanceFlags,
                governanceScore = it.governanceScore,
                isFlagged = it.isFlagged,
                flagReason = it.flagReason,
                relevanceScore = it.relevanceScore,
                sectorScores = it.sectorScores,
                mode = it.mode,
                isMarketing = it.isMarketing,
                marketingScore = it.marketingScore,
                analyzedAt = it.analyzedAt,
                model = it.model,
                provider = it.provider
            )
        }
    }

    /**
     * Get model name based on provider
     */
    private fun getModelName(): String {
        return when (aiServiceFactory.getCurrentProvider()) {
            AIProvider.OPENAI -> aiProperties.openai.model
            AIProvider.ANTHROPIC -> aiProperties.anthropic.model
            AIProvider.LOCAL -> aiProperties.local.model
        }
    }

    /**
     * Internal data classes for parsing
     */
    private data class GovernanceResult(
        val flags: List<GovernanceFlag>,
        val score: Double,
        val isFlagged: Boolean,
        val reason: String?
    )

    private data class FullAnalysis(
        val relevanceScore: Double,
        val sectorScores: List<SectorScore>,
        val mode: ContentMode,
        val isMarketing: Boolean,
        val marketingScore: Double
    )
}
