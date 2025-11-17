package com.intrigsoft.ipitch.interactionmanager.api

import com.intrigsoft.ipitch.domain.VoteTargetType
import com.intrigsoft.ipitch.interactionmanager.dto.request.CreateVoteRequest
import com.intrigsoft.ipitch.interactionmanager.dto.response.ApiResponse
import com.intrigsoft.ipitch.interactionmanager.dto.response.VoteResponse
import com.intrigsoft.ipitch.interactionmanager.dto.response.VoteStatsResponse
import com.intrigsoft.ipitch.interactionmanager.service.VoteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/votes")
@Tag(name = "Votes", description = "API for managing votes")
class VoteController(
    private val voteService: VoteService
) {

    @PostMapping
    @Operation(summary = "Create or update a vote")
    fun createOrUpdateVote(
        @Valid @RequestBody request: CreateVoteRequest
    ): ResponseEntity<ApiResponse<VoteResponse>> {
        logger.info { "Creating/updating vote for ${request.targetType}:${request.targetId}" }
        val vote = voteService.createOrUpdateVote(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("Vote saved successfully", vote))
    }

    @DeleteMapping("/{voteId}")
    @Operation(summary = "Delete a vote")
    fun deleteVote(
        @PathVariable voteId: UUID,
        @RequestParam userId: UUID
    ): ResponseEntity<ApiResponse<VoteResponse>> {
        logger.info { "Deleting vote $voteId" }
        val vote = voteService.deleteVote(voteId, userId)
        return ResponseEntity.ok(ApiResponse.success("Vote deleted successfully", vote))
    }

    @DeleteMapping
    @Operation(summary = "Remove a user's vote on a target")
    fun removeVote(
        @RequestParam userId: UUID,
        @RequestParam targetType: VoteTargetType,
        @RequestParam targetId: UUID
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.info { "Removing vote for $targetType:$targetId by user $userId" }
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
    @Operation(summary = "Get a user's vote on a specific target")
    fun getUserVote(
        @RequestParam userId: UUID,
        @RequestParam targetType: VoteTargetType,
        @RequestParam targetId: UUID
    ): ResponseEntity<ApiResponse<VoteResponse?>> {
        logger.info { "Fetching user vote for $targetType:$targetId by user $userId" }
        val vote = voteService.getUserVote(userId, targetType, targetId)
        return ResponseEntity.ok(ApiResponse.success("User vote retrieved successfully", vote))
    }

    @GetMapping("/stats")
    @Operation(summary = "Get vote statistics for a target")
    fun getVoteStats(
        @RequestParam targetType: VoteTargetType,
        @RequestParam targetId: UUID,
        @RequestParam(required = false) userId: UUID?
    ): ResponseEntity<ApiResponse<VoteStatsResponse>> {
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
