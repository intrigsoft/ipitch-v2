package com.intrigsoft.ipitch.interactionmanager.api

import com.intrigsoft.ipitch.domain.CommentTargetType
import com.intrigsoft.ipitch.interactionmanager.dto.request.CreateCommentRequest
import com.intrigsoft.ipitch.interactionmanager.dto.request.UpdateCommentRequest
import com.intrigsoft.ipitch.interactionmanager.dto.response.ApiResponse
import com.intrigsoft.ipitch.interactionmanager.dto.response.CommentResponse
import com.intrigsoft.ipitch.interactionmanager.service.CommentService
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
@RequestMapping("/api/comments")
@Tag(name = "Comments", description = "API for managing comments")
class CommentController(
    private val commentService: CommentService
) {

    @PostMapping
    @Operation(summary = "Create a new comment")
    fun createComment(
        @Valid @RequestBody request: CreateCommentRequest
    ): ResponseEntity<ApiResponse<CommentResponse>> {
        logger.info { "Creating comment for ${request.targetType}:${request.targetId}" }
        val comment = commentService.createComment(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("Comment created successfully", comment))
    }

    @PutMapping("/{commentId}")
    @Operation(summary = "Update a comment")
    fun updateComment(
        @PathVariable commentId: UUID,
        @Valid @RequestBody request: UpdateCommentRequest,
        @RequestParam userId: UUID
    ): ResponseEntity<ApiResponse<CommentResponse>> {
        logger.info { "Updating comment $commentId" }
        val comment = commentService.updateComment(commentId, request, userId)
        return ResponseEntity.ok(ApiResponse.success("Comment updated successfully", comment))
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Delete a comment (soft delete)")
    fun deleteComment(
        @PathVariable commentId: UUID,
        @RequestParam userId: UUID
    ): ResponseEntity<ApiResponse<CommentResponse>> {
        logger.info { "Deleting comment $commentId" }
        val comment = commentService.deleteComment(commentId, userId)
        return ResponseEntity.ok(ApiResponse.success("Comment deleted successfully", comment))
    }

    @GetMapping("/{commentId}")
    @Operation(summary = "Get a comment by ID")
    fun getComment(
        @PathVariable commentId: UUID,
        @RequestParam(required = false) userId: UUID?
    ): ResponseEntity<ApiResponse<CommentResponse>> {
        logger.info { "Fetching comment $commentId" }
        val comment = commentService.getComment(commentId, userId)
        return ResponseEntity.ok(ApiResponse.success("Comment retrieved successfully", comment))
    }

    @GetMapping
    @Operation(summary = "Get comments by target")
    fun getCommentsByTarget(
        @RequestParam targetType: CommentTargetType,
        @RequestParam targetId: UUID,
        @RequestParam(required = false) userId: UUID?
    ): ResponseEntity<ApiResponse<List<CommentResponse>>> {
        logger.info { "Fetching comments for $targetType:$targetId" }
        val comments = commentService.getCommentsByTarget(targetType, targetId, userId)
        return ResponseEntity.ok(ApiResponse.success("Comments retrieved successfully", comments))
    }

    @GetMapping("/{commentId}/replies")
    @Operation(summary = "Get replies to a comment")
    fun getReplies(
        @PathVariable commentId: UUID,
        @RequestParam(required = false) userId: UUID?
    ): ResponseEntity<ApiResponse<List<CommentResponse>>> {
        logger.info { "Fetching replies for comment $commentId" }
        val replies = commentService.getReplies(commentId, userId)
        return ResponseEntity.ok(ApiResponse.success("Replies retrieved successfully", replies))
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all comments by a user")
    fun getUserComments(
        @PathVariable userId: UUID
    ): ResponseEntity<ApiResponse<List<CommentResponse>>> {
        logger.info { "Fetching comments for user $userId" }
        val comments = commentService.getUserComments(userId)
        return ResponseEntity.ok(ApiResponse.success("User comments retrieved successfully", comments))
    }
}
