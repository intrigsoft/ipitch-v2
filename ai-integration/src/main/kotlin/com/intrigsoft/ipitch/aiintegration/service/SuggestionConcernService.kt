package com.intrigsoft.ipitch.aiintegration.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.intrigsoft.ipitch.aiintegration.config.AIProperties
import com.intrigsoft.ipitch.aiintegration.model.*
import com.intrigsoft.ipitch.aiintegration.repository.ProposalConcernRepository
import com.intrigsoft.ipitch.aiintegration.repository.ProposalSuggestionRepository
import com.intrigsoft.ipitch.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Service for extracting and managing suggestions and concerns from comments
 */
@Service
class SuggestionConcernService(
    private val aiServiceFactory: AIServiceFactory,
    private val objectMapper: ObjectMapper,
    private val vectorDatabaseService: VectorDatabaseService,
    private val suggestionRepository: ProposalSuggestionRepository,
    private val concernRepository: ProposalConcernRepository,
    private val aiProperties: AIProperties
) {

    private val logger = KotlinLogging.logger {}

    companion object {
        private const val SIMILARITY_THRESHOLD = 0.85 // Cosine similarity threshold for matching
        private const val ENTITY_TYPE_SUGGESTION = "SUGGESTION"
        private const val ENTITY_TYPE_CONCERN = "CONCERN"
    }

    /**
     * Extract suggestions and concerns from a comment
     */
    suspend fun extractSuggestionsAndConcerns(
        comment: Comment,
        proposal: Proposal,
        commentThread: List<Comment> = emptyList()
    ): SuggestionConcernExtractionResult = withContext(Dispatchers.IO) {
        logger.info { "Extracting suggestions and concerns from comment: ${comment.id}" }

        val aiService = aiServiceFactory.getAIService()

        val context = buildExtractionContext(comment, proposal, commentThread)

        val systemPrompt = """
            You are an expert analyst extracting actionable suggestions and concerns from comments on proposals.
            - Suggestions: Constructive ideas, improvements, or recommendations
            - Concerns: Issues, problems, risks, or objections raised

            Extract clear, concise, and distinct suggestions and concerns.
            Each should be self-contained and understandable without the original comment.
        """.trimIndent()

        val prompt = """
            Analyze this comment and extract any suggestions and concerns:

            $context

            Respond with JSON in this exact format:
            {
              "suggestions": [
                "First suggestion as a complete sentence",
                "Second suggestion as a complete sentence"
              ],
              "concerns": [
                "First concern as a complete sentence",
                "Second concern as a complete sentence"
              ]
            }

            - suggestions: Array of actionable suggestions (empty if none)
            - concerns: Array of issues or problems raised (empty if none)
            - Each should be self-contained and clear
            - Rephrase if needed to make each item understandable on its own
        """.trimIndent()

        val jsonResponse = aiService.generateStructuredResponse(
            prompt = prompt,
            systemPrompt = systemPrompt
        )

        parseExtractionResponse(jsonResponse, comment.id)
    }

    /**
     * Process extracted suggestions - check for similarity and create/link
     */
    @Transactional
    suspend fun processSuggestions(
        proposalId: UUID,
        commentId: UUID,
        suggestions: List<String>
    ): List<SuggestionProcessingResult> = withContext(Dispatchers.IO) {
        logger.info { "Processing ${suggestions.size} suggestions for proposal: $proposalId" }

        suggestions.map { suggestionText ->
            processSuggestion(proposalId, commentId, suggestionText)
        }
    }

    /**
     * Process extracted concerns - check for similarity and create/link
     */
    @Transactional
    suspend fun processConcerns(
        proposalId: UUID,
        commentId: UUID,
        concerns: List<String>
    ): List<ConcernProcessingResult> = withContext(Dispatchers.IO) {
        logger.info { "Processing ${concerns.size} concerns for proposal: $proposalId" }

        concerns.map { concernText ->
            processConcern(proposalId, commentId, concernText)
        }
    }

    /**
     * Process a single suggestion with similarity detection
     */
    private suspend fun processSuggestion(
        proposalId: UUID,
        commentId: UUID,
        suggestionText: String
    ): SuggestionProcessingResult {
        // Check for similar existing suggestions for this proposal
        val existingSuggestions = suggestionRepository.findByProposalId(proposalId)

        if (existingSuggestions.isNotEmpty()) {
            // Find similar suggestions using vector similarity
            val similar = findSimilarSuggestion(suggestionText, existingSuggestions)

            if (similar != null) {
                logger.info { "Found similar suggestion: ${similar.first.id} with similarity ${similar.second}" }

                // Add comment reference to existing suggestion
                val suggestionComment = SuggestionComment(
                    suggestion = similar.first,
                    commentId = commentId
                )

                similar.first.comments.add(suggestionComment)
                suggestionRepository.save(similar.first)

                return SuggestionProcessingResult(
                    suggestionId = similar.first.id,
                    text = similar.first.text,
                    isNew = false,
                    similarityScore = similar.second
                )
            }
        }

        // No similar suggestion found - create new one
        val embedding = vectorDatabaseService.storeEmbedding(
            entityType = ENTITY_TYPE_SUGGESTION,
            entityId = UUID.randomUUID(),
            text = suggestionText,
            model = getModelName()
        )

        val newSuggestion = ProposalSuggestion(
            proposalId = proposalId,
            text = suggestionText,
            embeddingId = embedding.id
        )

        val suggestionComment = SuggestionComment(
            suggestion = newSuggestion,
            commentId = commentId
        )

        newSuggestion.comments.add(suggestionComment)
        val saved = suggestionRepository.save(newSuggestion)

        logger.info { "Created new suggestion: ${saved.id}" }

        return SuggestionProcessingResult(
            suggestionId = saved.id,
            text = saved.text,
            isNew = true,
            similarityScore = null
        )
    }

    /**
     * Process a single concern with similarity detection
     */
    private suspend fun processConcern(
        proposalId: UUID,
        commentId: UUID,
        concernText: String
    ): ConcernProcessingResult {
        // Check for similar existing concerns for this proposal
        val existingConcerns = concernRepository.findByProposalId(proposalId)

        if (existingConcerns.isNotEmpty()) {
            // Find similar concerns using vector similarity
            val similar = findSimilarConcern(concernText, existingConcerns)

            if (similar != null) {
                logger.info { "Found similar concern: ${similar.first.id} with similarity ${similar.second}" }

                // Add comment reference to existing concern
                val concernComment = ConcernComment(
                    concern = similar.first,
                    commentId = commentId
                )

                similar.first.comments.add(concernComment)
                concernRepository.save(similar.first)

                return ConcernProcessingResult(
                    concernId = similar.first.id,
                    text = similar.first.text,
                    isNew = false,
                    similarityScore = similar.second
                )
            }
        }

        // No similar concern found - create new one
        val embedding = vectorDatabaseService.storeEmbedding(
            entityType = ENTITY_TYPE_CONCERN,
            entityId = UUID.randomUUID(),
            text = concernText,
            model = getModelName()
        )

        val newConcern = ProposalConcern(
            proposalId = proposalId,
            text = concernText,
            embeddingId = embedding.id
        )

        val concernComment = ConcernComment(
            concern = newConcern,
            commentId = commentId
        )

        newConcern.comments.add(concernComment)
        val saved = concernRepository.save(newConcern)

        logger.info { "Created new concern: ${saved.id}" }

        return ConcernProcessingResult(
            concernId = saved.id,
            text = saved.text,
            isNew = true,
            similarityScore = null
        )
    }

    /**
     * Find similar suggestion using vector similarity
     */
    private suspend fun findSimilarSuggestion(
        suggestionText: String,
        existingSuggestions: List<ProposalSuggestion>
    ): Pair<ProposalSuggestion, Double>? {
        // Use vector similarity search
        val similarEmbeddings = vectorDatabaseService.findSimilar(
            queryText = suggestionText,
            entityType = ENTITY_TYPE_SUGGESTION,
            topK = 5,
            similarityThreshold = SIMILARITY_THRESHOLD
        )

        if (similarEmbeddings.isEmpty()) {
            return null
        }

        // Find the matching suggestion with highest similarity
        val bestMatch = similarEmbeddings
            .mapNotNull { (embedding, similarity) ->
                existingSuggestions.find { it.embeddingId == embedding.id }
                    ?.let { it to similarity }
            }
            .maxByOrNull { it.second }

        return bestMatch
    }

    /**
     * Find similar concern using vector similarity
     */
    private suspend fun findSimilarConcern(
        concernText: String,
        existingConcerns: List<ProposalConcern>
    ): Pair<ProposalConcern, Double>? {
        // Use vector similarity search
        val similarEmbeddings = vectorDatabaseService.findSimilar(
            queryText = concernText,
            entityType = ENTITY_TYPE_CONCERN,
            topK = 5,
            similarityThreshold = SIMILARITY_THRESHOLD
        )

        if (similarEmbeddings.isEmpty()) {
            return null
        }

        // Find the matching concern with highest similarity
        val bestMatch = similarEmbeddings
            .mapNotNull { (embedding, similarity) ->
                existingConcerns.find { it.embeddingId == embedding.id }
                    ?.let { it to similarity }
            }
            .maxByOrNull { it.second }

        return bestMatch
    }

    /**
     * Build context for extraction
     */
    private fun buildExtractionContext(
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

        sb.appendLine("COMMENT TO ANALYZE:")
        sb.appendLine(comment.content)

        return sb.toString()
    }

    /**
     * Parse extraction response from AI
     */
    private fun parseExtractionResponse(jsonResponse: String, commentId: UUID): SuggestionConcernExtractionResult {
        try {
            val jsonNode = objectMapper.readTree(jsonResponse)

            val suggestions = mutableListOf<String>()
            jsonNode["suggestions"]?.forEach { node ->
                suggestions.add(node.asText())
            }

            val concerns = mutableListOf<String>()
            jsonNode["concerns"]?.forEach { node ->
                concerns.add(node.asText())
            }

            return SuggestionConcernExtractionResult(
                commentId = commentId,
                suggestions = suggestions,
                concerns = concerns
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse extraction response: $jsonResponse" }
            // Return empty result on parse failure
            return SuggestionConcernExtractionResult(
                commentId = commentId,
                suggestions = emptyList(),
                concerns = emptyList()
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
     * Get all suggestions for a proposal with comment references
     */
    @Transactional(readOnly = true)
    fun getSuggestionsForProposal(proposalId: UUID): List<ProposalSuggestion> {
        return suggestionRepository.findByProposalIdWithComments(proposalId)
    }

    /**
     * Get all concerns for a proposal with comment references
     */
    @Transactional(readOnly = true)
    fun getConcernsForProposal(proposalId: UUID): List<ProposalConcern> {
        return concernRepository.findByProposalIdWithComments(proposalId)
    }
}
