package com.intrigsoft.ipitch.domain

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.*

/**
 * Join table linking suggestions to the comments that contributed to them.
 */
@Entity
@Table(
    name = "suggestion_comments",
    indexes = [
        Index(name = "idx_suggestion_comments_suggestion_id", columnList = "suggestion_id"),
        Index(name = "idx_suggestion_comments_comment_id", columnList = "comment_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_suggestion_comment", columnNames = ["suggestion_id", "comment_id"])
    ]
)
data class SuggestionComment(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggestion_id", nullable = false, columnDefinition = "UUID")
    val suggestion: ProposalSuggestion,

    @Column(name = "comment_id", nullable = false, columnDefinition = "UUID")
    val commentId: UUID,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SuggestionComment) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String {
        return "SuggestionComment(id=$id, suggestionId=${suggestion.id}, commentId=$commentId)"
    }
}
