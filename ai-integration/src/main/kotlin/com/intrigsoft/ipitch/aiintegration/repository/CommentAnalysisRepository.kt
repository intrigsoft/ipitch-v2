package com.intrigsoft.ipitch.aiintegration.repository

import com.intrigsoft.ipitch.aiintegration.model.CommentAnalysis
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Repository for comment analysis results
 */
@Repository
interface CommentAnalysisRepository : JpaRepository<CommentAnalysis, UUID> {

    /**
     * Find analysis for a specific comment
     */
    fun findByCommentId(commentId: UUID): CommentAnalysis?

    /**
     * Check if analysis exists for a comment
     */
    fun existsByCommentId(commentId: UUID): Boolean

    /**
     * Find all flagged comments
     */
    fun findByIsFlagged(isFlagged: Boolean): List<CommentAnalysis>

    /**
     * Find all flagged comments for review
     */
    @Query("SELECT ca FROM CommentAnalysis ca WHERE ca.isFlagged = true ORDER BY ca.createdAt DESC")
    fun findAllFlaggedComments(): List<CommentAnalysis>

    /**
     * Find all marketing content
     */
    fun findByIsMarketing(isMarketing: Boolean): List<CommentAnalysis>

    /**
     * Delete analysis for a comment
     */
    fun deleteByCommentId(commentId: UUID)
}
