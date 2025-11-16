package com.example.ipitch.proposalmanager.service

import com.example.ipitch.proposalmanager.domain.*
import com.example.ipitch.proposalmanager.dto.request.*
import com.example.ipitch.proposalmanager.dto.response.*
import com.example.ipitch.proposalmanager.exception.*
import com.example.ipitch.proposalmanager.repository.*
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

private val logger = KotlinLogging.logger {}

@Service
@Transactional
class ProposalService(
    private val proposalRepository: ProposalRepository,
    private val contributorRepository: ContributorRepository,
    private val userRepository: UserRepository,
    private val gitService: GitService
) {

    /**
     * 1. Create a new proposal
     */
    fun createProposal(request: CreateProposalRequest): ProposalResponse {
        logger.info { "Creating new proposal: ${request.title} for owner ${request.ownerId}" }

        try {
            // Create proposal in database
            val proposal = Proposal(
                title = request.title,
                content = request.content,
                ownerId = request.ownerId,
                status = ProposalStatus.DRAFT
            )

            val savedProposal = proposalRepository.save(proposal)
            logger.info { "Proposal created in database with ID: ${savedProposal.id}" }

            // Create proposal in Git
            val workingBranch = gitService.createProposal(
                savedProposal.id!!,
                savedProposal.title,
                savedProposal.content
            )

            // Update proposal with Git info
            savedProposal.workingBranch = workingBranch
            savedProposal.updatedAt = LocalDateTime.now()
            val finalProposal = proposalRepository.save(savedProposal)

            logger.info { "Proposal ${finalProposal.id} created successfully with working branch: $workingBranch" }

            return toProposalResponse(finalProposal)
        } catch (e: Exception) {
            logger.error(e) { "Error creating proposal: ${request.title}" }
            throw e
        }
    }

    /**
     * 2. Update proposal metadata
     */
    fun updateProposalMetadata(proposalId: UUID, request: UpdateProposalMetadataRequest): ProposalResponse {
        logger.info { "Updating metadata for proposal $proposalId" }

        val proposal = proposalRepository.findById(proposalId)
            .orElseThrow { ProposalNotFoundException("Proposal not found: $proposalId") }

        request.status?.let {
            logger.debug { "Updating proposal status to $it" }
            proposal.status = it
        }

        request.stats?.let {
            logger.debug { "Updating proposal stats" }
            proposal.stats = it
        }

        proposal.updatedAt = LocalDateTime.now()
        val updatedProposal = proposalRepository.save(proposal)

        logger.info { "Proposal $proposalId metadata updated successfully" }

        return toProposalResponse(updatedProposal)
    }

    /**
     * 3. Add contributor to a proposal
     */
    fun addContributor(proposalId: UUID, request: AddContributorRequest): ContributorResponse {
        logger.info { "Adding contributor ${request.userId} to proposal $proposalId" }

        val proposal = proposalRepository.findById(proposalId)
            .orElseThrow { ProposalNotFoundException("Proposal not found: $proposalId") }

        // Check if contributor already exists
        val existing = contributorRepository.findByProposalAndUserId(proposal, request.userId)
        if (existing != null) {
            logger.warn { "Contributor ${request.userId} already exists for proposal $proposalId" }
            throw InvalidOperationException("Contributor already exists for this proposal")
        }

        // Create contributor
        val contributor = Contributor(
            userId = request.userId,
            role = request.role,
            status = ContributorStatus.ACTIVE,
            proposal = proposal
        )

        val savedContributor = contributorRepository.save(contributor)
        logger.info { "Contributor ${savedContributor.id} added to proposal $proposalId" }

        // Create contributor branch in Git
        try {
            val contributorBranch = gitService.createContributorBranch(proposalId, savedContributor.id!!)
            logger.info { "Contributor branch created: $contributorBranch" }
        } catch (e: Exception) {
            logger.error(e) { "Error creating contributor branch, but contributor was added to database" }
        }

        return toContributorResponse(savedContributor)
    }

    /**
     * 4. Remove contributor from a proposal
     */
    fun removeContributor(proposalId: UUID, contributorId: UUID): ApiResponse<Unit> {
        logger.info { "Removing contributor $contributorId from proposal $proposalId" }

        val proposal = proposalRepository.findById(proposalId)
            .orElseThrow { ProposalNotFoundException("Proposal not found: $proposalId") }

        val contributor = contributorRepository.findById(contributorId)
            .orElseThrow { ContributorNotFoundException("Contributor not found: $contributorId") }

        if (contributor.proposal?.id != proposalId) {
            throw InvalidOperationException("Contributor does not belong to this proposal")
        }

        contributorRepository.delete(contributor)
        logger.info { "Contributor $contributorId removed from proposal $proposalId" }

        return ApiResponse(
            success = true,
            message = "Contributor removed successfully"
        )
    }

    /**
     * 5. Update proposal content (Git commit on contributor branch)
     */
    fun updateContent(proposalId: UUID, request: UpdateContentRequest): ProposalResponse {
        logger.info { "Updating content for proposal $proposalId by contributor ${request.contributorId}" }

        val proposal = proposalRepository.findById(proposalId)
            .orElseThrow { ProposalNotFoundException("Proposal not found: $proposalId") }

        val contributor = contributorRepository.findById(request.contributorId)
            .orElseThrow { ContributorNotFoundException("Contributor not found: ${request.contributorId}") }

        if (contributor.proposal?.id != proposalId) {
            throw UnauthorizedOperationException("Contributor does not belong to this proposal")
        }

        if (contributor.status != ContributorStatus.ACTIVE) {
            throw UnauthorizedOperationException("Contributor is not active")
        }

        // Get user info for Git commit
        val authorName = "User-${contributor.userId}"
        val authorEmail = "${contributor.userId}@ipitch.com"

        // Commit to Git
        val commitHash = gitService.updateContent(
            proposalId = proposalId,
            contributorId = contributor.id!!,
            content = request.content,
            commitMessage = request.commitMessage,
            authorName = authorName,
            authorEmail = authorEmail
        )

        logger.info { "Content updated in Git with commit: $commitHash" }

        // Update proposal metadata
        proposal.content = request.content
        proposal.gitCommitHash = commitHash
        proposal.updatedAt = LocalDateTime.now()
        val updatedProposal = proposalRepository.save(proposal)

        return toProposalResponse(updatedProposal)
    }

    /**
     * 6. Update proposal title (Git commit on contributor branch)
     */
    fun updateTitle(proposalId: UUID, request: UpdateTitleRequest): ProposalResponse {
        logger.info { "Updating title for proposal $proposalId by contributor ${request.contributorId}" }

        val proposal = proposalRepository.findById(proposalId)
            .orElseThrow { ProposalNotFoundException("Proposal not found: $proposalId") }

        val contributor = contributorRepository.findById(request.contributorId)
            .orElseThrow { ContributorNotFoundException("Contributor not found: ${request.contributorId}") }

        if (contributor.proposal?.id != proposalId) {
            throw UnauthorizedOperationException("Contributor does not belong to this proposal")
        }

        if (contributor.status != ContributorStatus.ACTIVE) {
            throw UnauthorizedOperationException("Contributor is not active")
        }

        // Get user info for Git commit
        val authorName = "User-${contributor.userId}"
        val authorEmail = "${contributor.userId}@ipitch.com"

        // Commit to Git
        val commitHash = gitService.updateTitle(
            proposalId = proposalId,
            contributorId = contributor.id!!,
            title = request.title,
            commitMessage = request.commitMessage,
            authorName = authorName,
            authorEmail = authorEmail
        )

        logger.info { "Title updated in Git with commit: $commitHash" }

        // Update proposal metadata
        proposal.title = request.title
        proposal.gitCommitHash = commitHash
        proposal.updatedAt = LocalDateTime.now()
        val updatedProposal = proposalRepository.save(proposal)

        return toProposalResponse(updatedProposal)
    }

    /**
     * 7. Create pull request (contributor branch -> proposal working branch)
     */
    fun createPullRequest(proposalId: UUID, request: CreatePullRequestRequest): PullRequestResponse {
        logger.info { "Creating pull request for proposal $proposalId from contributor ${request.contributorId}" }

        val proposal = proposalRepository.findById(proposalId)
            .orElseThrow { ProposalNotFoundException("Proposal not found: $proposalId") }

        val contributor = contributorRepository.findById(request.contributorId)
            .orElseThrow { ContributorNotFoundException("Contributor not found: ${request.contributorId}") }

        if (contributor.proposal?.id != proposalId) {
            throw UnauthorizedOperationException("Contributor does not belong to this proposal")
        }

        // Create PR in Git
        val prId = gitService.createPullRequest(
            proposalId = proposalId,
            contributorId = contributor.id!!,
            description = request.description
        )

        logger.info { "Pull request created: $prId" }

        return PullRequestResponse(
            pullRequestId = prId,
            proposalId = proposalId,
            contributorId = contributor.id!!,
            sourceBranch = "proposal/$proposalId/contributor/${contributor.id}",
            targetBranch = "proposal/$proposalId",
            description = request.description,
            status = "OPEN",
            createdAt = LocalDateTime.now().toString()
        )
    }

    /**
     * 8. Merge pull request (only by proposal owner)
     */
    fun mergePullRequest(proposalId: UUID, ownerId: UUID, request: MergePullRequestRequest): ApiResponse<String> {
        logger.info { "Merging pull request ${request.pullRequestId} for proposal $proposalId" }

        val proposal = proposalRepository.findById(proposalId)
            .orElseThrow { ProposalNotFoundException("Proposal not found: $proposalId") }

        // Verify owner
        if (proposal.ownerId != ownerId) {
            throw UnauthorizedOperationException("Only the proposal owner can merge pull requests")
        }

        // Merge in Git
        val commitHash = gitService.mergePullRequest(
            pullRequestId = request.pullRequestId,
            commitMessage = request.commitMessage
        )

        logger.info { "Pull request merged with commit: $commitHash" }

        proposal.gitCommitHash = commitHash
        proposal.updatedAt = LocalDateTime.now()
        proposalRepository.save(proposal)

        return ApiResponse(
            success = true,
            message = "Pull request merged successfully",
            data = commitHash
        )
    }

    /**
     * 9. Publish proposal (merge to main and tag with version)
     */
    fun publishProposal(proposalId: UUID, ownerId: UUID): ProposalResponse {
        logger.info { "Publishing proposal $proposalId" }

        val proposal = proposalRepository.findById(proposalId)
            .orElseThrow { ProposalNotFoundException("Proposal not found: $proposalId") }

        // Verify owner
        if (proposal.ownerId != ownerId) {
            throw UnauthorizedOperationException("Only the proposal owner can publish proposals")
        }

        if (proposal.status != ProposalStatus.DRAFT) {
            throw InvalidOperationException("Only draft proposals can be published")
        }

        // Increment version
        val newVersion = incrementVersion(proposal.version)

        // Publish in Git (merge to main and tag)
        val commitHash = gitService.publishProposal(proposalId, newVersion)

        logger.info { "Proposal published with commit: $commitHash and version: $newVersion" }

        // Update proposal
        proposal.version = newVersion
        proposal.status = ProposalStatus.PUBLISHED
        proposal.gitCommitHash = commitHash
        proposal.updatedAt = LocalDateTime.now()
        val publishedProposal = proposalRepository.save(proposal)

        return toProposalResponse(publishedProposal)
    }

    /**
     * Get proposal by ID
     */
    fun getProposal(proposalId: UUID): ProposalResponse {
        logger.info { "Fetching proposal $proposalId" }

        val proposal = proposalRepository.findById(proposalId)
            .orElseThrow { ProposalNotFoundException("Proposal not found: $proposalId") }

        return toProposalResponse(proposal)
    }

    /**
     * Get all proposals
     */
    fun getAllProposals(): List<ProposalResponse> {
        logger.info { "Fetching all proposals" }
        return proposalRepository.findAll().map { toProposalResponse(it) }
    }

    /**
     * Helper: Convert Proposal to ProposalResponse
     */
    private fun toProposalResponse(proposal: Proposal): ProposalResponse {
        return ProposalResponse(
            id = proposal.id!!,
            title = proposal.title,
            content = proposal.content,
            ownerId = proposal.ownerId,
            contributors = proposal.contributors.map { toContributorResponse(it) },
            version = proposal.version,
            status = proposal.status,
            stats = proposal.stats,
            workingBranch = proposal.workingBranch,
            gitCommitHash = proposal.gitCommitHash,
            createdAt = proposal.createdAt,
            updatedAt = proposal.updatedAt
        )
    }

    /**
     * Helper: Convert Contributor to ContributorResponse
     */
    private fun toContributorResponse(contributor: Contributor): ContributorResponse {
        return ContributorResponse(
            id = contributor.id!!,
            userId = contributor.userId,
            role = contributor.role,
            status = contributor.status
        )
    }

    /**
     * Helper: Increment semantic version
     */
    private fun incrementVersion(currentVersion: String): String {
        val parts = currentVersion.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

        return "$major.$minor.${patch + 1}"
    }
}
