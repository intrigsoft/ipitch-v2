package com.intrigsoft.ipitch.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

@Entity
@Table(name = "users")
data class User(
    @Id
    @Column(name = "user_id", nullable = false)
    val userId: String, // Keycloak user ID

    @Column(nullable = false)
    val userName: String,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    @Column(name = "avatar_url")
    val avatarUrl: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    val scores: Map<String, Any> = emptyMap(), // Interests and maturity scores

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: UserStatus = UserStatus.ACTIVE,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "view_permissions", columnDefinition = "jsonb", nullable = false)
    val viewPermissions: UserViewPermissions = UserViewPermissions(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class UserStatus {
    ACTIVE,
    BLOCKED
}

data class UserViewPermissions(
    val showEmail: Boolean = true,
    val showScores: Boolean = true,
    val showDescription: Boolean = true
)
