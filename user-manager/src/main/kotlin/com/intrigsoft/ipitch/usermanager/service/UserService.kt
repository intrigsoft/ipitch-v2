package com.intrigsoft.ipitch.usermanager.service

import com.intrigsoft.ipitch.domain.User
import com.intrigsoft.ipitch.domain.UserStatus
import com.intrigsoft.ipitch.repository.UserRepository
import com.intrigsoft.ipitch.usermanager.dto.request.UpdateProfileRequest
import mu.KotlinLogging
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.UserRepresentation
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@Service
class UserService(
    private val userRepository: UserRepository,
    private val keycloak: Keycloak,
    @Value("\${keycloak.realm}") private val realm: String
) {

    @Transactional(readOnly = true)
    fun getUserProfile(userId: String): User {
        return userRepository.findById(userId).orElseGet {
            logger.info { "User not found in database, syncing from Keycloak: $userId" }
            syncUserFromKeycloak(userId)
        }
    }

    @Transactional
    fun updateUserProfile(userId: String, request: UpdateProfileRequest): User {
        val user = getUserProfile(userId)

        val updatedUser = user.copy(
            description = request.description ?: user.description,
            avatarUrl = request.avatarUrl ?: user.avatarUrl,
            viewPermissions = request.viewPermissions ?: user.viewPermissions,
            updatedAt = LocalDateTime.now()
        )

        return userRepository.save(updatedUser)
    }

    @Transactional
    fun syncUserFromKeycloak(userId: String): User {
        try {
            val keycloakUser = keycloak.realm(realm).users().get(userId).toRepresentation()

            val user = User(
                userId = userId,
                userName = keycloakUser.username ?: keycloakUser.firstName ?: "Unknown",
                email = keycloakUser.email ?: "",
                status = if (keycloakUser.isEnabled) UserStatus.ACTIVE else UserStatus.BLOCKED
            )

            return userRepository.save(user)
        } catch (e: Exception) {
            logger.error(e) { "Failed to sync user from Keycloak: $userId" }
            throw IllegalArgumentException("User not found: $userId")
        }
    }

    @Transactional
    fun createOrUpdateUserFromKeycloak(keycloakUser: UserRepresentation): User {
        val existingUser = userRepository.findById(keycloakUser.id)

        return if (existingUser.isPresent) {
            val user = existingUser.get()
            val updatedUser = user.copy(
                userName = keycloakUser.username ?: keycloakUser.firstName ?: user.userName,
                email = keycloakUser.email ?: user.email,
                status = if (keycloakUser.isEnabled) UserStatus.ACTIVE else UserStatus.BLOCKED,
                updatedAt = LocalDateTime.now()
            )
            userRepository.save(updatedUser)
        } else {
            val newUser = User(
                userId = keycloakUser.id,
                userName = keycloakUser.username ?: keycloakUser.firstName ?: "Unknown",
                email = keycloakUser.email ?: "",
                status = if (keycloakUser.isEnabled) UserStatus.ACTIVE else UserStatus.BLOCKED
            )
            userRepository.save(newUser)
        }
    }
}
