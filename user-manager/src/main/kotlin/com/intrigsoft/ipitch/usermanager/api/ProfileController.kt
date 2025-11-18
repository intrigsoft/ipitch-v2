package com.intrigsoft.ipitch.usermanager.api

import com.intrigsoft.ipitch.usermanager.dto.request.UpdateProfileRequest
import com.intrigsoft.ipitch.usermanager.dto.response.ProfileResponse
import com.intrigsoft.ipitch.usermanager.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "User Profile", description = "User profile management endpoints")
@SecurityRequirement(name = "bearer-jwt")
class ProfileController(
    private val userService: UserService,
    private val userScoreService: com.intrigsoft.ipitch.usermanager.service.UserScoreService
) {

    @GetMapping("/me")
    @Operation(
        summary = "Get own profile",
        description = "Returns the full profile of the authenticated user including all private information. If the profile is dirty, scores will be recalculated before returning."
    )
    fun getOwnProfile(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<ProfileResponse> {
        val userId = jwt.subject
        logger.info { "Getting profile for user: $userId" }

        // Recalculate scores if user is dirty
        userScoreService.recalculateScoresIfDirty(userId)

        val user = userService.getUserProfile(userId)
        return ResponseEntity.ok(ProfileResponse.fromUser(user, includePrivateInfo = true))
    }

    @GetMapping("/{userId}")
    @Operation(
        summary = "Get user profile by ID",
        description = "Returns the filtered profile of a user based on their privacy settings. If the profile is dirty, scores will be recalculated before returning."
    )
    fun getUserProfile(
        @PathVariable userId: String,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<ProfileResponse> {
        val requestingUserId = jwt.subject
        logger.info { "User $requestingUserId getting profile for user: $userId" }

        // Recalculate scores if user is dirty
        userScoreService.recalculateScoresIfDirty(userId)

        val user = userService.getUserProfile(userId)

        // If requesting own profile, return full info; otherwise return filtered
        val includePrivateInfo = requestingUserId == userId
        return ResponseEntity.ok(ProfileResponse.fromUser(user, includePrivateInfo))
    }

    @PutMapping("/me")
    @Operation(
        summary = "Update own profile",
        description = "Updates the profile of the authenticated user. Only description, avatar, and view permissions can be updated."
    )
    fun updateOwnProfile(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: UpdateProfileRequest
    ): ResponseEntity<ProfileResponse> {
        val userId = jwt.subject
        logger.info { "Updating profile for user: $userId" }

        val updatedUser = userService.updateUserProfile(userId, request)
        return ResponseEntity.ok(ProfileResponse.fromUser(updatedUser, includePrivateInfo = true))
    }
}
