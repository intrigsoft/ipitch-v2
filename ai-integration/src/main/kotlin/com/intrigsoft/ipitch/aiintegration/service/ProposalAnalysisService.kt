package com.intrigsoft.ipitch.aiintegration.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.intrigsoft.ipitch.aiintegration.config.AIProperties
import com.intrigsoft.ipitch.aiintegration.model.*
import com.intrigsoft.ipitch.aiintegration.repository.ProposalAnalysisRepository
import com.intrigsoft.ipitch.domain.Proposal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Service for AI-powered proposal analysis
 */
@Service
class ProposalAnalysisService(
    private val aiServiceFactory: AIServiceFactory,
    private val vectorDatabaseService: VectorDatabaseService,
    private val proposalAnalysisRepository: ProposalAnalysisRepository,
    private val aiProperties: AIProperties,
    private val objectMapper: ObjectMapper
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Analyze a proposal upon publication
     * - Generate summary
     * - Calculate clarity score
     * - Calculate sector-specific scores
     * - Index in vector database
     */
    @Transactional
    suspend fun analyzeProposal(proposal: Proposal): ProposalAnalysisResult = withContext(Dispatchers.IO) {
        logger.info { "Analyzing proposal: ${proposal.id}" }

        val aiService = aiServiceFactory.getAIService()
        val provider = aiServiceFactory.getCurrentProvider()

        // Prepare proposal text for analysis
        val proposalText = """
            Title: ${proposal.title}

            Content:
            ${proposal.content}
        """.trimIndent()

        // Generate structured analysis
        val analysisPrompt = buildAnalysisPrompt(proposalText)
        val systemPrompt = "You are an expert analyst evaluating proposals for quality, clarity, and relevance to various sectors."

        val jsonResponse = aiService.generateStructuredResponse(
            prompt = analysisPrompt,
            systemPrompt = systemPrompt
        )

        logger.debug { "Received AI analysis response" }

        // Parse response
        val analysis = parseAnalysisResponse(jsonResponse)

        // Index proposal in vector database
        val embedding = vectorDatabaseService.storeEmbedding(
            entityType = "PROPOSAL",
            entityId = proposal.id!!,
            text = proposalText,
            model = getModelName()
        )

        // Create result
        val result = ProposalAnalysisResult(
            proposalId = proposal.id,
            summary = analysis.summary,
            clarityScore = analysis.clarityScore,
            sectorScores = analysis.sectorScores,
            embeddingId = embedding.id,
            analyzedAt = Instant.now(),
            model = getModelName(),
            provider = provider
        )

        // Save to database
        saveAnalysisResult(result)

        logger.info { "Successfully analyzed proposal: ${proposal.id}" }
        result
    }

    /**
     * Get existing analysis for a proposal
     */
    @Transactional(readOnly = true)
    fun getProposalAnalysis(proposalId: UUID): ProposalAnalysisResult? {
        return proposalAnalysisRepository.findByProposalId(proposalId)?.let {
            ProposalAnalysisResult(
                proposalId = it.proposalId,
                summary = it.summary,
                clarityScore = it.clarityScore,
                sectorScores = it.sectorScores,
                embeddingId = it.embeddingId,
                analyzedAt = it.analyzedAt,
                model = it.model,
                provider = it.provider
            )
        }
    }

    /**
     * Find similar proposals using RAG
     */
    suspend fun findSimilarProposals(
        queryText: String,
        topK: Int = 5
    ): List<Pair<UUID, Double>> = withContext(Dispatchers.IO) {
        val results = vectorDatabaseService.findSimilar(
            queryText = queryText,
            entityType = "PROPOSAL",
            topK = topK
        )

        results.map { (embedding, similarity) ->
            embedding.entityId to similarity
        }
    }

    /**
     * Build analysis prompt
     */
    private fun buildAnalysisPrompt(proposalText: String): String {
        val sectors = aiProperties.sectors.enabled.joinToString(", ")

        return """
            Analyze the following proposal and provide:

            1. A concise summary (2-3 sentences) suitable for search results and TLDR sections
            2. A clarity score (0.0 to 10.0) evaluating how well-written and clear the proposal is
            3. Sector-specific relevance scores (0.0 to 10.0) for each applicable sector

            Available sectors: $sectors

            Only include sectors that are relevant to the proposal with a score > 0.

            Proposal:
            $proposalText

            Respond with JSON in this exact format:
            {
              "summary": "Brief summary here...",
              "clarityScore": 8.5,
              "sectorScores": [
                {"sector": "Healthcare", "score": 10.0},
                {"sector": "Education", "score": 6.5}
              ]
            }
        """.trimIndent()
    }

    /**
     * Parse AI analysis response
     */
    private fun parseAnalysisResponse(jsonResponse: String): ParsedAnalysis {
        try {
            val jsonNode = objectMapper.readTree(jsonResponse)

            val summary = jsonNode["summary"].asText()
            val clarityScore = jsonNode["clarityScore"].asDouble()

            val sectorScores = mutableListOf<SectorScore>()
            jsonNode["sectorScores"]?.forEach { node ->
                val sector = node["sector"].asText()
                val score = node["score"].asDouble()
                sectorScores.add(SectorScore(sector, score))
            }

            return ParsedAnalysis(summary, clarityScore, sectorScores)
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse analysis response: $jsonResponse" }
            throw Exception("Failed to parse AI analysis response", e)
        }
    }

    /**
     * Save analysis result to database
     */
    private fun saveAnalysisResult(result: ProposalAnalysisResult) {
        val entity = ProposalAnalysis(
            proposalId = result.proposalId,
            summary = result.summary,
            clarityScore = result.clarityScore,
            sectorScores = result.sectorScores,
            embeddingId = result.embeddingId,
            model = result.model,
            provider = result.provider,
            analyzedAt = result.analyzedAt,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        proposalAnalysisRepository.save(entity)
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
     * Internal data class for parsing
     */
    private data class ParsedAnalysis(
        val summary: String,
        val clarityScore: Double,
        val sectorScores: List<SectorScore>
    )
}
