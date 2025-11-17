package com.intrigsoft.ipitch.proposalviewmanager.api

import com.intrigsoft.ipitch.domain.ProposalStatus
import com.intrigsoft.ipitch.proposalviewmanager.dto.PagedProposalSearchResponse
import com.intrigsoft.ipitch.proposalviewmanager.dto.ProposalSearchRequest
import com.intrigsoft.ipitch.proposalviewmanager.dto.ProposalSearchResponse
import com.intrigsoft.ipitch.proposalviewmanager.service.ProposalSearchService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger {}

/**
 * Public API for viewing and searching proposals
 * This API is used by the frontend application
 */
@RestController
@RequestMapping("/api/v1/proposals")
@Tag(name = "Proposal View API", description = "Public APIs for viewing and searching proposals")
class ProposalViewController(
    private val proposalSearchService: ProposalSearchService
) {

    /**
     * Search proposals with advanced filtering
     */
    @GetMapping("/search")
    @Operation(summary = "Search proposals", description = "Search proposals with various filters")
    fun searchProposals(
        @Parameter(description = "Search query (searches in title and content)")
        @RequestParam(required = false) query: String?,

        @Parameter(description = "Filter by owner ID")
        @RequestParam(required = false) ownerId: String?,

        @Parameter(description = "Filter by status")
        @RequestParam(required = false) status: ProposalStatus?,

        @Parameter(description = "Filter by start date (ISO format)")
        @RequestParam(required = false) fromDate: String?,

        @Parameter(description = "Filter by end date (ISO format)")
        @RequestParam(required = false) toDate: String?,

        @Parameter(description = "Page number (0-indexed)")
        @RequestParam(defaultValue = "0") page: Int,

        @Parameter(description = "Page size")
        @RequestParam(defaultValue = "20") size: Int,

        @Parameter(description = "Sort field")
        @RequestParam(defaultValue = "updatedAt") sortBy: String,

        @Parameter(description = "Sort order (asc/desc)")
        @RequestParam(defaultValue = "desc") sortOrder: String
    ): ResponseEntity<PagedProposalSearchResponse> {
        logger.info { "Searching proposals with query: $query, ownerId: $ownerId, status: $status" }

        return try {
            val searchRequest = ProposalSearchRequest(
                query = query,
                ownerId = ownerId,
                status = status,
                fromDate = fromDate,
                toDate = toDate,
                page = page,
                size = size,
                sortBy = sortBy,
                sortOrder = sortOrder
            )

            val result = proposalSearchService.searchProposals(searchRequest)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            logger.error(e) { "Error searching proposals" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                PagedProposalSearchResponse(
                    content = emptyList(),
                    totalElements = 0,
                    totalPages = 0,
                    page = page,
                    size = size
                )
            )
        }
    }

    /**
     * Get a proposal by ID
     */
    @GetMapping("/{proposalId}")
    @Operation(summary = "Get proposal by ID", description = "Retrieve a single proposal by its ID")
    fun getProposalById(
        @Parameter(description = "Proposal ID")
        @PathVariable proposalId: String
    ): ResponseEntity<ProposalSearchResponse> {
        logger.info { "Getting proposal by ID: $proposalId" }

        return try {
            val proposal = proposalSearchService.getProposalById(proposalId)
            if (proposal != null) {
                ResponseEntity.ok(proposal)
            } else {
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            logger.error(e) { "Error getting proposal: $proposalId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /**
     * Get all proposals (paginated)
     */
    @GetMapping
    @Operation(summary = "Get all proposals", description = "Retrieve all proposals with pagination")
    fun getAllProposals(
        @Parameter(description = "Page number (0-indexed)")
        @RequestParam(defaultValue = "0") page: Int,

        @Parameter(description = "Page size")
        @RequestParam(defaultValue = "20") size: Int,

        @Parameter(description = "Sort field")
        @RequestParam(defaultValue = "updatedAt") sortBy: String,

        @Parameter(description = "Sort order (asc/desc)")
        @RequestParam(defaultValue = "desc") sortOrder: String
    ): ResponseEntity<PagedProposalSearchResponse> {
        logger.info { "Getting all proposals, page: $page, size: $size" }

        return try {
            val searchRequest = ProposalSearchRequest(
                page = page,
                size = size,
                sortBy = sortBy,
                sortOrder = sortOrder
            )

            val result = proposalSearchService.searchProposals(searchRequest)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            logger.error(e) { "Error getting all proposals" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                PagedProposalSearchResponse(
                    content = emptyList(),
                    totalElements = 0,
                    totalPages = 0,
                    page = page,
                    size = size
                )
            )
        }
    }
}
