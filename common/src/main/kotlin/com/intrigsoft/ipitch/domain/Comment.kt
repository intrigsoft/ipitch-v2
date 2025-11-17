package com.intrigsoft.ipitch.domain

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "comments",
    indexes = [
        Index(name = "idx_comment_target", columnList = "targetType,targetId"),
        Index(name = "idx_comment_user", columnList = "userId"),
        Index(name = "idx_comment_parent", columnList = "parent_comment_id")
    ]
)
data class Comment(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val userId: UUID,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

    // Parent comment for tree structure (null if top-level comment)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    var parentComment: Comment? = null,

    // Polymorphic reference to the entity being commented on
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val targetType: CommentTargetType,

    @Column(nullable = false)
    val targetId: UUID,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    // Soft delete flag
    @Column(nullable = false)
    var deleted: Boolean = false
)

enum class CommentTargetType {
    PROPOSAL,
    COMMENT,
    INFERRED_ENTITY
}
