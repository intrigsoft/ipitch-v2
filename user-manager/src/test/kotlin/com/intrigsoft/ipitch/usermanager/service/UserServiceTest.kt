package com.intrigsoft.ipitch.usermanager.service

import com.intrigsoft.ipitch.domain.User
import com.intrigsoft.ipitch.domain.UserStatus
import com.intrigsoft.ipitch.domain.UserViewPermissions
import com.intrigsoft.ipitch.repository.UserRepository
import com.intrigsoft.ipitch.usermanager.dto.request.UpdateProfileRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.admin.client.resource.UsersResource
import org.keycloak.representations.idm.UserRepresentation
import java.util.*

class UserServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var keycloak: Keycloak
    private lateinit var userService: UserService

    private val testUserId = "test-user-id-123"
    private val testUser = User(
        userId = testUserId,
        userName = "testuser",
        email = "test@example.com",
        description = "Test user description",
        avatarUrl = "https://example.com/avatar.jpg",
        scores = mapOf("interest" to "tech", "maturity" to 5),
        status = UserStatus.ACTIVE,
        viewPermissions = UserViewPermissions(
            showEmail = true,
            showScores = true,
            showDescription = true
        )
    )

    @BeforeEach
    fun setup() {
        userRepository = mockk()
        keycloak = mockk()
        userService = UserService(userRepository, keycloak, "ipitch")
    }

    @Test
    fun `getUserProfile should return existing user from database`() {
        // Given
        every { userRepository.findById(testUserId) } returns Optional.of(testUser)

        // When
        val result = userService.getUserProfile(testUserId)

        // Then
        assertEquals(testUser, result)
        verify(exactly = 1) { userRepository.findById(testUserId) }
    }

    @Test
    fun `getUserProfile should sync from Keycloak if user not found in database`() {
        // Given
        val keycloakUserRepresentation = UserRepresentation().apply {
            id = testUserId
            username = "testuser"
            email = "test@example.com"
            isEnabled = true
        }

        val userResource: UserResource = mockk()
        val usersResource: UsersResource = mockk()
        val realmResource: RealmResource = mockk()

        every { userRepository.findById(testUserId) } returns Optional.empty()
        every { keycloak.realm("ipitch") } returns realmResource
        every { realmResource.users() } returns usersResource
        every { usersResource.get(testUserId) } returns userResource
        every { userResource.toRepresentation() } returns keycloakUserRepresentation
        every { userRepository.save(any()) } returns testUser

        // When
        val result = userService.getUserProfile(testUserId)

        // Then
        assertNotNull(result)
        verify(exactly = 1) { userRepository.findById(testUserId) }
        verify(exactly = 1) { userRepository.save(any()) }
    }

    @Test
    fun `updateUserProfile should update only editable fields`() {
        // Given
        val updateRequest = UpdateProfileRequest(
            description = "Updated description",
            avatarUrl = "https://example.com/new-avatar.jpg",
            viewPermissions = UserViewPermissions(
                showEmail = false,
                showScores = true,
                showDescription = false
            )
        )

        every { userRepository.findById(testUserId) } returns Optional.of(testUser)
        every { userRepository.save(any()) } answers { firstArg() }

        // When
        val result = userService.updateUserProfile(testUserId, updateRequest)

        // Then
        assertEquals("Updated description", result.description)
        assertEquals("https://example.com/new-avatar.jpg", result.avatarUrl)
        assertEquals(false, result.viewPermissions.showEmail)
        assertEquals(true, result.viewPermissions.showScores)
        assertEquals(false, result.viewPermissions.showDescription)

        // These fields should remain unchanged
        assertEquals(testUser.userName, result.userName)
        assertEquals(testUser.email, result.email)
        assertEquals(testUser.scores, result.scores)
        assertEquals(testUser.status, result.status)

        verify(exactly = 1) { userRepository.save(any()) }
    }

    @Test
    fun `updateUserProfile should keep existing values if request fields are null`() {
        // Given
        val updateRequest = UpdateProfileRequest(
            description = null,
            avatarUrl = null,
            viewPermissions = null
        )

        every { userRepository.findById(testUserId) } returns Optional.of(testUser)
        every { userRepository.save(any()) } answers { firstArg() }

        // When
        val result = userService.updateUserProfile(testUserId, updateRequest)

        // Then
        assertEquals(testUser.description, result.description)
        assertEquals(testUser.avatarUrl, result.avatarUrl)
        assertEquals(testUser.viewPermissions, result.viewPermissions)

        verify(exactly = 1) { userRepository.save(any()) }
    }
}
