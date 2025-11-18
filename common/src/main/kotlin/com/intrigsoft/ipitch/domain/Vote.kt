package com.intrigsoft.ipitch.domain

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "votes",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_vote_user_target",
            columnNames = ["userId", "targetType", "targetId"]
        )
    ],
    indexes = [
        Index(name = "idx_vote_target", columnList = "targetType,targetId"),
        Index(name = "idx_vote_user", columnList = "userId")
    ]
)
data class Vote(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val userId: String, // Keycloak user ID

    // Polymorphic reference to the entity being voted on
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val targetType: VoteTargetType,

    @Column(nullable = false)
    val targetId: UUID,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var voteType: VoteType,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class VoteTargetType {
    PROPOSAL,
    COMMENT,
    INFERRED_ENTITY
}

enum class VoteType {
    UP,
    DOWN
}
