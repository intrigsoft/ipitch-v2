package com.intrigsoft.ipitch.domain

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.*

/**
 * Join table linking concerns to the comments that contributed to them.
 */
@Entity
@Table(
    name = "concern_comments",
    indexes = [
        Index(name = "idx_concern_comments_concern_id", columnList = "concern_id"),
        Index(name = "idx_concern_comments_comment_id", columnList = "comment_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_concern_comment", columnNames = ["concern_id", "comment_id"])
    ]
)
data class ConcernComment(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concern_id", nullable = false, columnDefinition = "UUID")
    val concern: ProposalConcern,

    @Column(name = "comment_id", nullable = false, columnDefinition = "UUID")
    val commentId: UUID,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConcernComment) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String {
        return "ConcernComment(id=$id, concernId=${concern.id}, commentId=$commentId)"
    }
}
