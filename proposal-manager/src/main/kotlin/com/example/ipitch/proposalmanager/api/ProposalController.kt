package com.example.ipitch.proposalmanager.api

import com.example.ipitch.proposalmanager.dto.request.*
import com.example.ipitch.proposalmanager.dto.response.*
import com.example.ipitch.proposalmanager.service.ProposalService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/proposals")
@Tag(name = "Proposal Management", description = "APIs for managing proposals with Git-based version control")
class ProposalController(
    private val proposalService: ProposalService
) {

    @PostMapping
    @Operation(
        summary = "Create a new proposal",
        description = "Creates a new proposal in the database and initializes a Git repository structure with a working branch"
    )
    fun createProposal(
        @Valid @RequestBody request: CreateProposalRequest
    ): ResponseEntity<ApiResponse<ProposalResponse>> {
        logger.info { "API: Creating proposal with title: ${request.title}" }

        val proposal = proposalService.createProposal(request)

        logger.info { "API: Proposal created successfully with ID: ${proposal.id}" }

        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse(
                success = true,
                message = "Proposal created successfully",
                data = proposal
            )
        )
    }

    @PutMapping("/{proposalId}/metadata")
    @Operation(
        summary = "Update proposal metadata",
        description = "Updates proposal metadata such as status and stats without affecting Git content"
    )
    fun updateProposalMetadata(
        @Parameter(description = "Proposal ID") @PathVariable proposalId: UUID,
        @Valid @RequestBody request: UpdateProposalMetadataRequest
    ): ResponseEntity<ApiResponse<ProposalResponse>> {
        logger.info { "API: Updating metadata for proposal $proposalId" }

        val proposal = proposalService.updateProposalMetadata(proposalId, request)

        logger.info { "API: Metadata updated successfully for proposal $proposalId" }

        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Proposal metadata updated successfully",
                data = proposal
            )
        )
    }

    @PostMapping("/{proposalId}/contributors")
    @Operation(
        summary = "Add a contributor",
        description = "Adds a contributor to the proposal and creates a dedicated Git branch for the contributor"
    )
    fun addContributor(
        @Parameter(description = "Proposal ID") @PathVariable proposalId: UUID,
        @Valid @RequestBody request: AddContributorRequest
    ): ResponseEntity<ApiResponse<ContributorResponse>> {
        logger.info { "API: Adding contributor ${request.userId} to proposal $proposalId" }

        val contributor = proposalService.addContributor(proposalId, request)

        logger.info { "API: Contributor added successfully to proposal $proposalId" }

        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse(
                success = true,
                message = "Contributor added successfully",
                data = contributor
            )
        )
    }

    @DeleteMapping("/{proposalId}/contributors/{contributorId}")
    @Operation(
        summary = "Remove a contributor",
        description = "Removes a contributor from the proposal"
    )
    fun removeContributor(
        @Parameter(description = "Proposal ID") @PathVariable proposalId: UUID,
        @Parameter(description = "Contributor ID") @PathVariable contributorId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        logger.info { "API: Removing contributor $contributorId from proposal $proposalId" }

        val response = proposalService.removeContributor(proposalId, contributorId)

        logger.info { "API: Contributor removed successfully from proposal $proposalId" }

        return ResponseEntity.ok(response)
    }

    @PutMapping("/{proposalId}/content")
    @Operation(
        summary = "Update proposal content",
        description = "Updates the proposal content and commits the changes to the contributor's Git branch"
    )
    fun updateContent(
        @Parameter(description = "Proposal ID") @PathVariable proposalId: UUID,
        @Valid @RequestBody request: UpdateContentRequest
    ): ResponseEntity<ApiResponse<ProposalResponse>> {
        logger.info { "API: Updating content for proposal $proposalId by contributor ${request.contributorId}" }

        val proposal = proposalService.updateContent(proposalId, request)

        logger.info { "API: Content updated and committed for proposal $proposalId" }

        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Content updated successfully",
                data = proposal
            )
        )
    }

    @PutMapping("/{proposalId}/title")
    @Operation(
        summary = "Update proposal title",
        description = "Updates the proposal title and commits the changes to the contributor's Git branch"
    )
    fun updateTitle(
        @Parameter(description = "Proposal ID") @PathVariable proposalId: UUID,
        @Valid @RequestBody request: UpdateTitleRequest
    ): ResponseEntity<ApiResponse<ProposalResponse>> {
        logger.info { "API: Updating title for proposal $proposalId by contributor ${request.contributorId}" }

        val proposal = proposalService.updateTitle(proposalId, request)

        logger.info { "API: Title updated and committed for proposal $proposalId" }

        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Title updated successfully",
                data = proposal
            )
        )
    }

    @PostMapping("/{proposalId}/pull-requests")
    @Operation(
        summary = "Create a pull request",
        description = "Creates a pull request from contributor's branch to the proposal's working branch"
    )
    fun createPullRequest(
        @Parameter(description = "Proposal ID") @PathVariable proposalId: UUID,
        @Valid @RequestBody request: CreatePullRequestRequest
    ): ResponseEntity<ApiResponse<PullRequestResponse>> {
        logger.info { "API: Creating pull request for proposal $proposalId from contributor ${request.contributorId}" }

        val pullRequest = proposalService.createPullRequest(proposalId, request)

        logger.info { "API: Pull request created: ${pullRequest.pullRequestId}" }

        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiResponse(
                success = true,
                message = "Pull request created successfully",
                data = pullRequest
            )
        )
    }

    @PostMapping("/{proposalId}/pull-requests/merge")
    @Operation(
        summary = "Merge a pull request",
        description = "Merges a pull request into the proposal's working branch (owner only)"
    )
    fun mergePullRequest(
        @Parameter(description = "Proposal ID") @PathVariable proposalId: UUID,
        @Parameter(description = "Owner User ID") @RequestParam ownerId: UUID,
        @Valid @RequestBody request: MergePullRequestRequest
    ): ResponseEntity<ApiResponse<String>> {
        logger.info { "API: Merging pull request ${request.pullRequestId} for proposal $proposalId" }

        val response = proposalService.mergePullRequest(proposalId, ownerId, request)

        logger.info { "API: Pull request merged successfully" }

        return ResponseEntity.ok(response)
    }

    @PostMapping("/{proposalId}/publish")
    @Operation(
        summary = "Publish a proposal",
        description = "Publishes the proposal by merging to main branch and creating a version tag"
    )
    fun publishProposal(
        @Parameter(description = "Proposal ID") @PathVariable proposalId: UUID,
        @Parameter(description = "Owner User ID") @RequestParam ownerId: UUID
    ): ResponseEntity<ApiResponse<ProposalResponse>> {
        logger.info { "API: Publishing proposal $proposalId by owner $ownerId" }

        val proposal = proposalService.publishProposal(proposalId, ownerId)

        logger.info { "API: Proposal published successfully with version ${proposal.version}" }

        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Proposal published successfully with version ${proposal.version}",
                data = proposal
            )
        )
    }

    @GetMapping("/{proposalId}")
    @Operation(
        summary = "Get a proposal",
        description = "Retrieves a proposal by its ID"
    )
    fun getProposal(
        @Parameter(description = "Proposal ID") @PathVariable proposalId: UUID
    ): ResponseEntity<ApiResponse<ProposalResponse>> {
        logger.info { "API: Fetching proposal $proposalId" }

        val proposal = proposalService.getProposal(proposalId)

        logger.info { "API: Proposal fetched successfully: $proposalId" }

        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Proposal retrieved successfully",
                data = proposal
            )
        )
    }

    @GetMapping
    @Operation(
        summary = "Get all proposals",
        description = "Retrieves all proposals in the system"
    )
    fun getAllProposals(): ResponseEntity<ApiResponse<List<ProposalResponse>>> {
        logger.info { "API: Fetching all proposals" }

        val proposals = proposalService.getAllProposals()

        logger.info { "API: Fetched ${proposals.size} proposals" }

        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                message = "Proposals retrieved successfully",
                data = proposals
            )
        )
    }
}
