package com.intrigsoft.ipitch.usermanager.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.intrigsoft.ipitch.domain.User
import com.intrigsoft.ipitch.domain.UserStatus
import com.intrigsoft.ipitch.domain.UserViewPermissions
import com.intrigsoft.ipitch.usermanager.dto.request.UpdateProfileRequest
import com.intrigsoft.ipitch.usermanager.service.UserService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(ProfileController::class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ProfileControllerTest.TestConfig::class)
class ProfileControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @TestConfiguration
    class TestConfig {
        @Bean
        fun userService(): UserService = mockk()
    }

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

    @Test
    fun `getOwnProfile should return full profile for authenticated user`() {
        // Given
        every { userService.getUserProfile(testUserId) } returns testUser

        // When & Then
        mockMvc.perform(
            get("/api/v1/profile/me")
                .with(jwt().jwt { it.subject(testUserId) })
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(testUserId))
            .andExpect(jsonPath("$.userName").value("testuser"))
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.description").value("Test user description"))
            .andExpect(jsonPath("$.avatarUrl").value("https://example.com/avatar.jpg"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }

    @Test
    fun `getUserProfile should return filtered profile for other users`() {
        // Given
        val otherUserId = "other-user-id-456"
        val userWithPrivateSettings = testUser.copy(
            viewPermissions = UserViewPermissions(
                showEmail = false,
                showScores = false,
                showDescription = false
            )
        )
        every { userService.getUserProfile(testUserId) } returns userWithPrivateSettings

        // When & Then
        mockMvc.perform(
            get("/api/v1/profile/$testUserId")
                .with(jwt().jwt { it.subject(otherUserId) })
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(testUserId))
            .andExpect(jsonPath("$.userName").value("testuser"))
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.scores").doesNotExist())
            .andExpect(jsonPath("$.description").doesNotExist())
            .andExpect(jsonPath("$.avatarUrl").value("https://example.com/avatar.jpg"))
    }

    @Test
    fun `getUserProfile should return full profile when requesting own profile`() {
        // Given
        every { userService.getUserProfile(testUserId) } returns testUser

        // When & Then
        mockMvc.perform(
            get("/api/v1/profile/$testUserId")
                .with(jwt().jwt { it.subject(testUserId) })
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(testUserId))
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.description").value("Test user description"))
    }

    @Test
    fun `updateOwnProfile should update profile successfully`() {
        // Given
        val updateRequest = UpdateProfileRequest(
            description = "Updated description",
            avatarUrl = "https://example.com/new-avatar.jpg",
            viewPermissions = UserViewPermissions(showEmail = false, showScores = true, showDescription = true)
        )

        val updatedUser = testUser.copy(
            description = "Updated description",
            avatarUrl = "https://example.com/new-avatar.jpg",
            viewPermissions = UserViewPermissions(showEmail = false, showScores = true, showDescription = true)
        )

        every { userService.updateUserProfile(testUserId, updateRequest) } returns updatedUser

        // When & Then
        mockMvc.perform(
            put("/api/v1/profile/me")
                .with(jwt().jwt { it.subject(testUserId) })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.description").value("Updated description"))
            .andExpect(jsonPath("$.avatarUrl").value("https://example.com/new-avatar.jpg"))
            .andExpect(jsonPath("$.viewPermissions.showEmail").value(false))
    }

    @Test
    fun `endpoints should require authentication`() {
        // When & Then
        mockMvc.perform(get("/api/v1/profile/me"))
            .andExpect(status().isUnauthorized)

        mockMvc.perform(get("/api/v1/profile/$testUserId"))
            .andExpect(status().isUnauthorized)

        mockMvc.perform(
            put("/api/v1/profile/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isUnauthorized)
    }
}
