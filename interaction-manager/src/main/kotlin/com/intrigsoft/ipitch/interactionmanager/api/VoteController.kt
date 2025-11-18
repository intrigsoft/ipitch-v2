package com.intrigsoft.ipitch.interactionmanager.api

import com.intrigsoft.ipitch.domain.VoteTargetType
import com.intrigsoft.ipitch.interactionmanager.dto.request.CreateVoteRequest
import com.intrigsoft.ipitch.interactionmanager.dto.response.ApiResponse
import com.intrigsoft.ipitch.interactionmanager.dto.response.VoteResponse
import com.intrigsoft.ipitch.interactionmanager.dto.response.VoteStatsResponse
import com.intrigsoft.ipitch.interactionmanager.service.VoteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/votes")
@Tag(name = "Votes", description = "API for managing votes")
@SecurityRequirement(name = "bearer-jwt")
class VoteController(
    private val voteService: VoteService
) {

    @PostMapping
    @Operation(summary = "Create or update a vote", description = "Creates or updates a vote by the authenticated user")
    fun createOrUpdateVote(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: CreateVoteRequest
    ): ResponseEntity<ApiResponse<VoteResponse>> {
        val userId = jwt.subject
        logger.info { "User $userId creating/updating vote for ${request.targetType}:${request.targetId}" }

        // Override userId with authenticated user to ensure security
        val secureRequest = request.copy(userId = userId)
        val vote = voteService.createOrUpdateVote(secureRequest)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("Vote saved successfully", vote))
    }

    @DeleteMapping("/{voteId}")
    @Operation(summary = "Delete a vote", description = "Deletes a vote. Only the vote owner can delete it.")
    fun deleteVote(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable voteId: UUID
    ): ResponseEntity<ApiResponse<VoteResponse>> {
        val userId = jwt.subject
        logger.info { "User $userId deleting vote $voteId" }
        val vote = voteService.deleteVote(voteId, userId)
        return ResponseEntity.ok(ApiResponse.success("Vote deleted successfully", vote))
    }

    @DeleteMapping
    @Operation(summary = "Remove a user's vote on a target", description = "Removes the authenticated user's vote on a specific target")
    fun removeVote(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam targetType: VoteTargetType,
        @RequestParam targetId: UUID
    ): ResponseEntity<ApiResponse<Nothing>> {
        val userId = jwt.subject
        logger.info { "User $userId removing vote for $targetType:$targetId" }
        voteService.removeVote(userId, targetType, targetId)
        return ResponseEntity.ok(ApiResponse.success("Vote removed successfully"))
    }

    @GetMapping("/{voteId}")
    @Operation(summary = "Get a vote by ID")
    fun getVote(
        @PathVariable voteId: UUID
    ): ResponseEntity<ApiResponse<VoteResponse>> {
        logger.info { "Fetching vote $voteId" }
        val vote = voteService.getVote(voteId)
        return ResponseEntity.ok(ApiResponse.success("Vote retrieved successfully", vote))
    }

    @GetMapping("/user")
    @Operation(summary = "Get own vote on a specific target")
    fun getUserVote(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam targetType: VoteTargetType,
        @RequestParam targetId: UUID
    ): ResponseEntity<ApiResponse<VoteResponse?>> {
        val userId = jwt.subject
        logger.info { "User $userId fetching own vote for $targetType:$targetId" }
        val vote = voteService.getUserVote(userId, targetType, targetId)
        return ResponseEntity.ok(ApiResponse.success("User vote retrieved successfully", vote))
    }

    @GetMapping("/stats")
    @Operation(summary = "Get vote statistics for a target")
    fun getVoteStats(
        @AuthenticationPrincipal jwt: Jwt?,
        @RequestParam targetType: VoteTargetType,
        @RequestParam targetId: UUID
    ): ResponseEntity<ApiResponse<VoteStatsResponse>> {
        val userId = jwt?.subject
        logger.info { "Fetching vote stats for $targetType:$targetId" }
        val stats = voteService.getVoteStats(targetType, targetId, userId)
        return ResponseEntity.ok(ApiResponse.success("Vote stats retrieved successfully", stats))
    }

    @GetMapping
    @Operation(summary = "Get all votes for a target")
    fun getVotesByTarget(
        @RequestParam targetType: VoteTargetType,
        @RequestParam targetId: UUID
    ): ResponseEntity<ApiResponse<List<VoteResponse>>> {
        logger.info { "Fetching votes for $targetType:$targetId" }
        val votes = voteService.getVotesByTarget(targetType, targetId)
        return ResponseEntity.ok(ApiResponse.success("Votes retrieved successfully", votes))
    }
}
