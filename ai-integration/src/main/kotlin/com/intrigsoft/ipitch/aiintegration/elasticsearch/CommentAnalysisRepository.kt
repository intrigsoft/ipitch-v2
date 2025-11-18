package com.intrigsoft.ipitch.aiintegration.elasticsearch

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.annotations.Query
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository

/**
 * Elasticsearch repository for comment analysis documents
 */
@Repository
interface CommentAnalysisElasticsearchRepository : ElasticsearchRepository<CommentAnalysisDocument, String> {

    /**
     * Find comment analysis by comment ID
     */
    fun findByCommentId(commentId: String): CommentAnalysisDocument?

    /**
     * Find all analyses by user (for user analytics)
     */
    fun findByUserId(userId: String, pageable: Pageable): Page<CommentAnalysisDocument>

    /**
     * Find all flagged comments
     */
    fun findByIsFlagged(isFlagged: Boolean, pageable: Pageable): Page<CommentAnalysisDocument>

    /**
     * Find all flagged comments by a specific user (for user moderation tracking)
     */
    fun findByUserIdAndIsFlagged(userId: String, isFlagged: Boolean, pageable: Pageable): Page<CommentAnalysisDocument>

    /**
     * Find all marketing/spam comments
     */
    fun findByIsMarketing(isMarketing: Boolean, pageable: Pageable): Page<CommentAnalysisDocument>

    /**
     * Find comments by mode (for user behavior analysis)
     */
    fun findByMode(mode: String, pageable: Pageable): Page<CommentAnalysisDocument>

    /**
     * Find comments by user and mode (for user behavior profile)
     */
    fun findByUserIdAndMode(userId: String, mode: String, pageable: Pageable): Page<CommentAnalysisDocument>

    /**
     * Find comments by relevance score range
     */
    fun findByRelevanceScoreBetween(minScore: Double, maxScore: Double, pageable: Pageable): Page<CommentAnalysisDocument>

    /**
     * Find comments by user with minimum relevance score (for user quality metrics)
     */
    fun findByUserIdAndRelevanceScoreGreaterThanEqual(
        userId: String,
        minRelevanceScore: Double,
        pageable: Pageable
    ): Page<CommentAnalysisDocument>

    /**
     * Find comments on a specific proposal
     */
    fun findByProposalId(proposalId: String, pageable: Pageable): Page<CommentAnalysisDocument>

    /**
     * Find comments on a proposal with specific mode (for proposal engagement analysis)
     */
    fun findByProposalIdAndMode(proposalId: String, mode: String, pageable: Pageable): Page<CommentAnalysisDocument>

    /**
     * Find flagged comments on a specific proposal
     */
    fun findByProposalIdAndIsFlagged(proposalId: String, isFlagged: Boolean, pageable: Pageable): Page<CommentAnalysisDocument>

    /**
     * Search comments by content
     */
    @Query("""
        {
            "match": {
                "commentContent": "?0"
            }
        }
    """)
    fun searchByContent(query: String, pageable: Pageable): Page<CommentAnalysisDocument>

    /**
     * Find comments by specific governance flag (for moderation analytics)
     */
    @Query("""
        {
            "term": {
                "governanceFlags": "?0"
            }
        }
    """)
    fun findByGovernanceFlag(flag: String, pageable: Pageable): Page<CommentAnalysisDocument>
}
