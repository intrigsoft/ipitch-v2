package com.intrigsoft.ipitch.usermanager.dto.response

import com.intrigsoft.ipitch.domain.User
import com.intrigsoft.ipitch.domain.UserStatus
import com.intrigsoft.ipitch.domain.UserViewPermissions
import java.time.LocalDateTime

data class ProfileResponse(
    val userId: String,
    val userName: String,
    val email: String?,
    val description: String?,
    val avatarUrl: String?,
    val scores: Map<String, Any>?,
    val status: UserStatus,
    val viewPermissions: UserViewPermissions,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun fromUser(user: User, includePrivateInfo: Boolean = true): ProfileResponse {
            return if (includePrivateInfo) {
                // Full profile with all information
                ProfileResponse(
                    userId = user.userId,
                    userName = user.userName,
                    email = user.email,
                    description = user.description,
                    avatarUrl = user.avatarUrl,
                    scores = user.scores,
                    status = user.status,
                    viewPermissions = user.viewPermissions,
                    createdAt = user.createdAt,
                    updatedAt = user.updatedAt
                )
            } else {
                // Filtered profile based on viewPermissions
                ProfileResponse(
                    userId = user.userId,
                    userName = user.userName,
                    email = if (user.viewPermissions.showEmail) user.email else null,
                    description = if (user.viewPermissions.showDescription) user.description else null,
                    avatarUrl = user.avatarUrl,
                    scores = if (user.viewPermissions.showScores) user.scores else null,
                    status = user.status,
                    viewPermissions = user.viewPermissions,
                    createdAt = user.createdAt,
                    updatedAt = user.updatedAt
                )
            }
        }
    }
}
