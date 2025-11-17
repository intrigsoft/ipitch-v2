package com.intrigsoft.ipitch.proposalviewmanager.api

import com.intrigsoft.ipitch.proposalviewmanager.dto.ApiResponse
import com.intrigsoft.ipitch.proposalviewmanager.dto.ProposalPublishDto
import com.intrigsoft.ipitch.proposalviewmanager.service.ProposalIndexService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

/**
 * Internal API for proposal management operations
 * This API is called by proposal-manager service to index proposals in Elasticsearch
 */
@RestController
@RequestMapping("/internal/api/v1/proposals")
@Tag(name = "Internal Proposal API", description = "Internal APIs for proposal indexing")
class InternalProposalController(
    private val proposalIndexService: ProposalIndexService
) {

    /**
     * Publish/Index a proposal
     * Called by proposal-manager when a proposal is created or updated
     */
    @PostMapping("/publish")
    @Operation(summary = "Publish a proposal", description = "Index or update a proposal in Elasticsearch")
    fun publishProposal(
        @RequestBody publishDto: ProposalPublishDto
    ): ResponseEntity<ApiResponse<String>> {
        logger.info { "Received request to publish proposal: ${publishDto.id}" }

        return try {
            proposalIndexService.indexProposal(publishDto)
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = "Proposal published successfully",
                    data = publishDto.id.toString()
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Error publishing proposal: ${publishDto.id}" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse(
                    success = false,
                    message = "Error publishing proposal: ${e.message}"
                )
            )
        }
    }

    /**
     * Delete a proposal from index
     * Called by proposal-manager when a proposal is deleted
     */
    @DeleteMapping("/{proposalId}")
    @Operation(summary = "Delete a proposal", description = "Remove a proposal from Elasticsearch index")
    fun deleteProposal(
        @PathVariable proposalId: String
    ): ResponseEntity<ApiResponse<String>> {
        logger.info { "Received request to delete proposal: $proposalId" }

        return try {
            proposalIndexService.deleteProposal(proposalId)
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    message = "Proposal deleted successfully",
                    data = proposalId
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Error deleting proposal: $proposalId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse(
                    success = false,
                    message = "Error deleting proposal: ${e.message}"
                )
            )
        }
    }
}
