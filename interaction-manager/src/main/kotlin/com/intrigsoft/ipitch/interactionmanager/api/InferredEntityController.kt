package com.intrigsoft.ipitch.interactionmanager.api

import com.intrigsoft.ipitch.domain.InferredEntityStatus
import com.intrigsoft.ipitch.domain.InferredEntityType
import com.intrigsoft.ipitch.interactionmanager.dto.request.UpdateInferredEntityContentRequest
import com.intrigsoft.ipitch.interactionmanager.dto.request.UpdateInferredEntityStatusRequest
import com.intrigsoft.ipitch.interactionmanager.dto.response.ApiResponse
import com.intrigsoft.ipitch.interactionmanager.dto.response.InferredEntityResponse
import com.intrigsoft.ipitch.interactionmanager.service.InferredEntityService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/inferred-entities")
@Tag(name = "Inferred Entities", description = "API for managing inferred entities (suggestions, concerns, etc.)")
class InferredEntityController(
    private val inferredEntityService: InferredEntityService
) {

    @GetMapping("/{id}")
    @Operation(summary = "Get an inferred entity by ID")
    fun getInferredEntity(
        @PathVariable id: UUID,
        @RequestParam(required = false) userId: String?
    ): ResponseEntity<ApiResponse<InferredEntityResponse>> {
        logger.info { "Fetching inferred entity $id" }
        val entity = inferredEntityService.getInferredEntity(id, userId)
        return ResponseEntity.ok(ApiResponse.success("Inferred entity retrieved successfully", entity))
    }

    @GetMapping("/proposal/{proposalId}")
    @Operation(summary = "Get all inferred entities for a proposal")
    fun getInferredEntitiesByProposal(
        @PathVariable proposalId: UUID,
        @RequestParam(required = false) userId: String?,
        @RequestParam(required = false) entityType: InferredEntityType?,
        @RequestParam(required = false) status: InferredEntityStatus?
    ): ResponseEntity<ApiResponse<List<InferredEntityResponse>>> {
        logger.info { "Fetching inferred entities for proposal $proposalId" }
        val entities = inferredEntityService.getInferredEntitiesByProposal(
            proposalId,
            userId,
            entityType,
            status
        )
        return ResponseEntity.ok(ApiResponse.success("Inferred entities retrieved successfully", entities))
    }

    @GetMapping("/comment/{commentId}")
    @Operation(summary = "Get all inferred entities extracted from a comment")
    fun getInferredEntitiesByComment(
        @PathVariable commentId: UUID,
        @RequestParam(required = false) userId: String?
    ): ResponseEntity<ApiResponse<List<InferredEntityResponse>>> {
        logger.info { "Fetching inferred entities for comment $commentId" }
        val entities = inferredEntityService.getInferredEntitiesByComment(commentId, userId)
        return ResponseEntity.ok(ApiResponse.success("Inferred entities retrieved successfully", entities))
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update the status of an inferred entity")
    fun updateStatus(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateInferredEntityStatusRequest
    ): ResponseEntity<ApiResponse<InferredEntityResponse>> {
        logger.info { "Updating inferred entity $id status to ${request.status}" }
        val entity = inferredEntityService.updateStatus(id, request.status, request.reviewerId)
        return ResponseEntity.ok(ApiResponse.success("Inferred entity status updated successfully", entity))
    }

    @PutMapping("/{id}/content")
    @Operation(summary = "Update the content of an inferred entity")
    fun updateContent(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateInferredEntityContentRequest
    ): ResponseEntity<ApiResponse<InferredEntityResponse>> {
        logger.info { "Updating inferred entity $id content" }
        val entity = inferredEntityService.updateContent(id, request.content, request.summary)
        return ResponseEntity.ok(ApiResponse.success("Inferred entity content updated successfully", entity))
    }
}
