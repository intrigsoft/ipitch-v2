package com.intrigsoft.ipitch.repository

import com.intrigsoft.ipitch.domain.Comment
import com.intrigsoft.ipitch.domain.CommentTargetType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface CommentRepository : JpaRepository<Comment, UUID> {
    fun findByTargetTypeAndTargetIdAndDeletedFalse(
        targetType: CommentTargetType,
        targetId: UUID
    ): List<Comment>

    fun findByTargetTypeAndTargetIdAndParentCommentIsNullAndDeletedFalse(
        targetType: CommentTargetType,
        targetId: UUID
    ): List<Comment>

    fun findByParentCommentAndDeletedFalse(parentComment: Comment): List<Comment>

    fun findByUserIdAndDeletedFalse(userId: String): List<Comment>

    @Query("""
        SELECT c FROM Comment c
        WHERE c.parentComment.id = :parentId
        AND c.deleted = false
        ORDER BY c.createdAt ASC
    """)
    fun findRepliesByParentId(parentId: UUID): List<Comment>

    fun countByTargetTypeAndTargetIdAndDeletedFalse(
        targetType: CommentTargetType,
        targetId: UUID
    ): Long
}
