package com.intrigsoft.ipitch.aiintegration.service

import com.intrigsoft.ipitch.aiintegration.elasticsearch.CommentAnalysisDocument
import com.intrigsoft.ipitch.aiintegration.elasticsearch.CommentAnalysisElasticsearchRepository
import com.intrigsoft.ipitch.aiintegration.elasticsearch.ProposalAnalysisDocument
import com.intrigsoft.ipitch.aiintegration.elasticsearch.ProposalAnalysisElasticsearchRepository
import com.intrigsoft.ipitch.aiintegration.model.CommentAnalysis
import com.intrigsoft.ipitch.aiintegration.model.ProposalAnalysis
import com.intrigsoft.ipitch.domain.Comment
import com.intrigsoft.ipitch.domain.Proposal
import com.intrigsoft.ipitch.repository.CommentRepository
import com.intrigsoft.ipitch.repository.ProposalRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Service for syncing AI analysis results to Elasticsearch
 * Enables search and user behavior analytics
 */
@Service
class AnalysisElasticsearchSyncService(
    private val proposalAnalysisElasticsearchRepository: ProposalAnalysisElasticsearchRepository,
    private val commentAnalysisElasticsearchRepository: CommentAnalysisElasticsearchRepository,
    private val proposalRepository: ProposalRepository,
    private val commentRepository: CommentRepository
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Sync proposal analysis to Elasticsearch
     */
    fun syncProposalAnalysis(analysis: ProposalAnalysis, proposal: Proposal) {
        try {
            logger.debug { "Syncing proposal analysis to Elasticsearch: ${analysis.proposalId}" }

            val document = ProposalAnalysisDocument.from(analysis, proposal)
            proposalAnalysisElasticsearchRepository.save(document)

            logger.info { "Proposal analysis synced to Elasticsearch: ${analysis.proposalId}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to sync proposal analysis to Elasticsearch: ${analysis.proposalId}" }
            // Don't fail the operation if Elasticsearch sync fails
        }
    }

    /**
     * Sync comment analysis to Elasticsearch
     * @param proposalId The root proposal ID for context (can be null if not determinable)
     */
    fun syncCommentAnalysis(analysis: CommentAnalysis, comment: Comment, proposalId: UUID?) {
        try {
            logger.debug { "Syncing comment analysis to Elasticsearch: ${analysis.commentId}" }

            val document = CommentAnalysisDocument.from(
                analysis,
                comment,
                proposalId?.toString()
            )
            commentAnalysisElasticsearchRepository.save(document)

            logger.info { "Comment analysis synced to Elasticsearch: ${analysis.commentId}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to sync comment analysis to Elasticsearch: ${analysis.commentId}" }
            // Don't fail the operation if Elasticsearch sync fails
        }
    }

    /**
     * Delete proposal analysis from Elasticsearch
     */
    fun deleteProposalAnalysis(proposalId: UUID) {
        try {
            val document = proposalAnalysisElasticsearchRepository.findByProposalId(proposalId.toString())
            document?.let {
                proposalAnalysisElasticsearchRepository.delete(it)
                logger.info { "Proposal analysis deleted from Elasticsearch: $proposalId" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete proposal analysis from Elasticsearch: $proposalId" }
        }
    }

    /**
     * Delete comment analysis from Elasticsearch
     */
    fun deleteCommentAnalysis(commentId: UUID) {
        try {
            val document = commentAnalysisElasticsearchRepository.findByCommentId(commentId.toString())
            document?.let {
                commentAnalysisElasticsearchRepository.delete(it)
                logger.info { "Comment analysis deleted from Elasticsearch: $commentId" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete comment analysis from Elasticsearch: $commentId" }
        }
    }

    /**
     * Reindex all proposal analyses (for maintenance/migration)
     */
    fun reindexAllProposalAnalyses() {
        try {
            logger.info { "Starting reindex of all proposal analyses" }
            // Implementation would batch process all proposals
            // This is a placeholder for future implementation
            logger.warn { "Reindex not yet implemented - would process all proposals in batches" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to reindex proposal analyses" }
        }
    }

    /**
     * Reindex all comment analyses (for maintenance/migration)
     */
    fun reindexAllCommentAnalyses() {
        try {
            logger.info { "Starting reindex of all comment analyses" }
            // Implementation would batch process all comments
            // This is a placeholder for future implementation
            logger.warn { "Reindex not yet implemented - would process all comments in batches" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to reindex comment analyses" }
        }
    }
}
