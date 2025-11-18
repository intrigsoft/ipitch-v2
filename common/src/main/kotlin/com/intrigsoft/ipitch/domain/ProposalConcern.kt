package com.intrigsoft.ipitch.domain

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.*

/**
 * Represents a concern extracted from comments on a proposal.
 * Multiple comments may contribute to the same concern if they are semantically similar.
 */
@Entity
@Table(
    name = "proposal_concerns",
    indexes = [
        Index(name = "idx_concerns_proposal_id", columnList = "proposal_id"),
        Index(name = "idx_concerns_embedding_id", columnList = "embedding_id")
    ]
)
data class ProposalConcern(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "proposal_id", nullable = false, columnDefinition = "UUID")
    val proposalId: UUID,

    @Column(nullable = false, columnDefinition = "TEXT")
    var text: String,

    @Column(name = "embedding_id", columnDefinition = "UUID")
    var embeddingId: UUID? = null,

    @OneToMany(mappedBy = "concern", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val comments: MutableList<ConcernComment> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProposalConcern) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String {
        return "ProposalConcern(id=$id, proposalId=$proposalId, text='${text.take(50)}...', embeddingId=$embeddingId)"
    }
}
