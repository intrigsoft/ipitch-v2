package com.intrigsoft.ipitch.proposalmanager.service

import com.intrigsoft.ipitch.domain.*
import com.intrigsoft.ipitch.repository.*
import com.intrigsoft.ipitch.proposalmanager.dto.request.*
import com.intrigsoft.ipitch.proposalmanager.dto.response.*
import com.intrigsoft.ipitch.proposalmanager.exception.*
import com.intrigsoft.ipitch.aiintegration.service.ProposalAnalysisService
import kotlinx.coroutines.runBlocking
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
    private val gitService: GitService,
    private val proposalViewManagerClient: com.intrigsoft.ipitch.proposalmanager.client.ProposalViewManagerClient,
    private val proposalAnalysisService: ProposalAnalysisService? = null,
    private val suggestionConcernService: com.intrigsoft.ipitch.aiintegration.service.SuggestionConcernService? = null
) {

    /**
     * 1. Create a new proposal
     */
    fun createProposal(request: CreateProposalRequest): ProposalResponse {
        val startTime = System.currentTimeMillis()
        logger.info { "[PROPOSAL-CHANGE] Creating new proposal - title: '${request.title}', owner: ${request.ownerId}, contentLength: ${request.content.length}" }

        try {
            // Get or create user for git attribution
            val owner = getOrCreateUser(request.ownerId)
            logger.debug { "[PROPOSAL-CHANGE] Owner resolved - userId: ${owner.userId}, name: ${owner.getGitAuthorName()}, email: ${owner.getGitAuthorEmail()}" }

            // Create proposal in database
            val proposal = Proposal(
                title = request.title,
                content = request.content,
                ownerId = request.ownerId,
                status = ProposalStatus.DRAFT
            )

            val savedProposal = proposalRepository.save(proposal)
            logger.info { "[PROPOSAL-CHANGE] Proposal created in database - id: ${savedProposal.id}, status: DRAFT" }

            // Create proposal in Git with user's git credentials
            val workingBranch = gitService.createProposal(
                savedProposal.id!!,
                savedProposal.title,
                savedProposal.content,
                owner.getGitAuthorName(),
                owner.getGitAuthorEmail()
            )

            // Update proposal with Git info
            savedProposal.workingBranch = workingBranch
            savedProposal.updatedAt = LocalDateTime.now()
            val finalProposal = proposalRepository.save(savedProposal)

            val duration = System.currentTimeMillis() - startTime
            logger.info { "[PROPOSAL-CHANGE] Proposal creation completed - id: ${finalProposal.id}, branch: $workingBranch, author: ${owner.getGitAuthorName()}, duration: ${duration}ms" }

            return toProposalResponse(finalProposal)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error(e) { "[PROPOSAL-CHANGE] Failed to create proposal - title: '${request.title}', owner: ${request.ownerId}, duration: ${duration}ms, error: ${e.message}" }
            throw e
        }
    }

    /**
     * 2. Update proposal metadata
     */
    fun updateProposalMetadata(proposalId: UUID, request: UpdateProposalMetadataRequest): ProposalResponse {
        val startTime = System.currentTimeMillis()
        logger.info { "[PROPOSAL-CHANGE] Updating metadata for proposal $proposalId" }

        val proposal = proposalRepository.findById(proposalId)
            .orElseThrow { ProposalNotFoundException("Proposal not found: $proposalId") }

        val oldStatus = proposal.status
        val oldStats = proposal.stats.toString()

        request.status?.let {
            logger.info { "[PROPOSAL-CHANGE] Status change - proposalId: $proposalId, from: $oldStatus, to: $it" }
            proposal.status = it
        }

        request.stats?.let {
            logger.info { "[PROPOSAL-CHANGE] Stats update - proposalId: $proposalId, oldStats: $oldStats, newStats: $it" }
            proposal.stats = it
        }

        proposal.updatedAt = LocalDateTime.now()
        val updatedProposal = proposalRepository.save(proposal)

        val duration = System.currentTimeMillis() - startTime
        logger.info { "[PROPOSAL-CHANGE] Metadata update completed - proposalId: $proposalId, duration: ${duration}ms" }

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
        val startTime = System.currentTimeMillis()
        logger.info { "[PROPOSAL-CHANGE] Updating content - proposalId: $proposalId, contributorId: ${request.contributorId}, newContentLength: ${request.content.length}" }

        val proposal = proposalRepository.findById(proposalId)
            .orElseThrow { ProposalNotFoundException("Proposal not found: $proposalId") }

        val oldContent = proposal.content
        val oldContentLength = oldContent.length

        val contributor = contributorRepository.findById(request.contributorId)
            .orElseThrow { ContributorNotFoundException("Contributor not found: ${request.contributorId}") }

        if (contributor.proposal?.id != proposalId) {
            throw UnauthorizedOperationException("Contributor does not belong to this proposal")
        }

        if (contributor.status != ContributorStatus.ACTIVE) {
            throw UnauthorizedOperationException("Contributor is not active")
        }

        // Get user info for Git commit
        val user = getOrCreateUser(contributor.userId)
        logger.debug { "[PROPOSAL-CHANGE] Contributor details - userId: ${user.userId}, name: ${user.getGitAuthorName()}, email: ${user.getGitAuthorEmail()}" }

        // Commit to Git with user's git credentials
        val commitHash = gitService.updateContent(
            proposalId = proposalId,
            contributorId = contributor.id!!,
            content = request.content,
            commitMessage = request.commitMessage,
            authorName = user.getGitAuthorName(),
            authorEmail = user.getGitAuthorEmail()
        )

        logger.info { "[PROPOSAL-CHANGE] Git commit created - commitHash: $commitHash, author: ${user.getGitAuthorName()}, message: '${request.commitMessage}'" }

        // Update proposal metadata
        proposal.content = request.content
        proposal.gitCommitHash = commitHash
        proposal.updatedAt = LocalDateTime.now()
        val updatedProposal = proposalRepository.save(proposal)

        val duration = System.currentTimeMillis() - startTime
        logger.info { "[PROPOSAL-CHANGE] Content update completed - proposalId: $proposalId, oldLength: $oldContentLength, newLength: ${request.content.length}, commitHash: $commitHash, duration: ${duration}ms" }

        return toProposalResponse(updatedProposal)
    }

    /**
     * 6. Update proposal title (Git commit on contributor branch)
     */
    fun updateTitle(proposalId: UUID, request: UpdateTitleRequest): ProposalResponse {
        val startTime = System.currentTimeMillis()
        logger.info { "[PROPOSAL-CHANGE] Updating title - proposalId: $proposalId, contributorId: ${request.contributorId}" }

        val proposal = proposalRepository.findById(proposalId)
            .orElseThrow { ProposalNotFoundException("Proposal not found: $proposalId") }

        val oldTitle = proposal.title
        logger.info { "[PROPOSAL-CHANGE] Title change - proposalId: $proposalId, oldTitle: '$oldTitle', newTitle: '${request.title}'" }

        val contributor = contributorRepository.findById(request.contributorId)
            .orElseThrow { ContributorNotFoundException("Contributor not found: ${request.contributorId}") }

        if (contributor.proposal?.id != proposalId) {
            throw UnauthorizedOperationException("Contributor does not belong to this proposal")
        }

        if (contributor.status != ContributorStatus.ACTIVE) {
            throw UnauthorizedOperationException("Contributor is not active")
        }

        // Get user info for Git commit
        val user = getOrCreateUser(contributor.userId)
        logger.debug { "[PROPOSAL-CHANGE] Contributor details - userId: ${user.userId}, name: ${user.getGitAuthorName()}" }

        // Commit to Git with user's git credentials
        val commitHash = gitService.updateTitle(
            proposalId = proposalId,
            contributorId = contributor.id!!,
            title = request.title,
            commitMessage = request.commitMessage,
            authorName = user.getGitAuthorName(),
            authorEmail = user.getGitAuthorEmail()
        )

        logger.info { "[PROPOSAL-CHANGE] Git commit created - commitHash: $commitHash, author: ${user.getGitAuthorName()}, message: '${request.commitMessage}'" }

        // Update proposal metadata
        proposal.title = request.title
        proposal.gitCommitHash = commitHash
        proposal.updatedAt = LocalDateTime.now()
        val updatedProposal = proposalRepository.save(proposal)

        val duration = System.currentTimeMillis() - startTime
        logger.info { "[PROPOSAL-CHANGE] Title update completed - proposalId: $proposalId, commitHash: $commitHash, duration: ${duration}ms" }

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
    fun mergePullRequest(proposalId: UUID, ownerId: String, request: MergePullRequestRequest): ApiResponse<String> {
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
    fun publishProposal(proposalId: UUID, ownerId: String): ProposalResponse {
        val startTime = System.currentTimeMillis()
        logger.info { "[PROPOSAL-CHANGE] Publishing proposal - proposalId: $proposalId, ownerId: $ownerId" }

        val proposal = proposalRepository.findById(proposalId)
            .orElseThrow { ProposalNotFoundException("Proposal not found: $proposalId") }

        logger.info { "[PROPOSAL-CHANGE] Proposal details before publish - id: $proposalId, title: '${proposal.title}', currentVersion: ${proposal.version}, status: ${proposal.status}, contributors: ${proposal.contributors.size}" }

        // Verify owner
        if (proposal.ownerId != ownerId) {
            throw UnauthorizedOperationException("Only the proposal owner can publish proposals")
        }

        if (proposal.status != ProposalStatus.DRAFT) {
            throw InvalidOperationException("Only draft proposals can be published")
        }

        // Increment version
        val newVersion = incrementVersion(proposal.version)
        logger.info { "[PROPOSAL-CHANGE] Version increment - oldVersion: ${proposal.version}, newVersion: $newVersion" }

        // Publish in Git (merge to main and tag)
        val commitHash = gitService.publishProposal(proposalId, newVersion)
        logger.info { "[PROPOSAL-CHANGE] Git publish completed - commitHash: $commitHash, version tag: $newVersion" }

        // Update proposal
        proposal.version = newVersion
        proposal.status = ProposalStatus.PUBLISHED
        proposal.gitCommitHash = commitHash
        proposal.updatedAt = LocalDateTime.now()
        val publishedProposal = proposalRepository.save(proposal)

        logger.info { "[PROPOSAL-CHANGE] Proposal status changed - proposalId: $proposalId, from: DRAFT, to: PUBLISHED, version: $newVersion" }

        // Mark all contributors (including owner) as dirty for score recalculation
        markContributorsAsDirty(publishedProposal)

        // AI Analysis: Analyze proposal, generate summary, sector scores, clarity score, and index in vector DB
        try {
            proposalAnalysisService?.let {
                runBlocking {
                    logger.info { "[PROPOSAL-CHANGE] Starting AI analysis for proposal $proposalId" }
                    val analysisResult = it.analyzeProposal(publishedProposal)
                    logger.info { "[PROPOSAL-CHANGE] AI analysis completed - proposalId: $proposalId, clarityScore: ${analysisResult.clarityScore}, summary length: ${analysisResult.summary.length}" }
                    logger.debug { "[PROPOSAL-CHANGE] Sector scores: ${analysisResult.sectorScores}" }
                }
            } ?: logger.warn { "[PROPOSAL-CHANGE] ProposalAnalysisService not available, skipping AI analysis for proposal $proposalId" }
        } catch (e: Exception) {
            logger.error(e) { "[PROPOSAL-CHANGE] AI analysis failed for proposal $proposalId, but proposal was published successfully - error: ${e.message}" }
            // Don't fail the publication if AI analysis fails
        }

        // Publish to view manager for indexing
        try {
            val publishDto = com.intrigsoft.ipitch.proposalmanager.client.ProposalPublishDto(
                id = publishedProposal.id!!,
                title = publishedProposal.title,
                content = publishedProposal.content,
                ownerId = publishedProposal.ownerId,
                ownerName = "User-${publishedProposal.ownerId}",
                contributors = publishedProposal.contributors.map { contributor ->
                    com.intrigsoft.ipitch.proposalmanager.client.ContributorDto(
                        id = contributor.id!!,
                        userId = contributor.userId,
                        userName = "User-${contributor.userId}",
                        role = contributor.role,
                        status = contributor.status.name
                    )
                },
                version = publishedProposal.version,
                status = publishedProposal.status.name,
                stats = publishedProposal.stats,
                workingBranch = publishedProposal.workingBranch,
                gitCommitHash = publishedProposal.gitCommitHash,
                createdAt = publishedProposal.createdAt,
                updatedAt = publishedProposal.updatedAt
            )
            proposalViewManagerClient.publishProposal(publishDto)
            logger.info { "[PROPOSAL-CHANGE] Proposal indexed in view manager - proposalId: $proposalId" }
        } catch (e: Exception) {
            logger.error(e) { "[PROPOSAL-CHANGE] Failed to index in view manager - proposalId: $proposalId, error: ${e.message}" }
            // Don't fail the operation if view manager is down
        }

        val totalDuration = System.currentTimeMillis() - startTime
        logger.info { "[PROPOSAL-CHANGE] Proposal published successfully - proposalId: $proposalId, version: $newVersion, totalDuration: ${totalDuration}ms" }

        return toProposalResponse(publishedProposal)
    }

    /**
     * 10. Revert proposal to previous version
     */
    fun revertProposal(proposalId: UUID, ownerId: String): ProposalResponse {
        val startTime = System.currentTimeMillis()
        logger.info { "[PROPOSAL-CHANGE] Reverting proposal - proposalId: $proposalId, ownerId: $ownerId" }

        val proposal = proposalRepository.findById(proposalId)
            .orElseThrow { ProposalNotFoundException("Proposal not found: $proposalId") }

        logger.info { "[PROPOSAL-CHANGE] Proposal state before revert - id: $proposalId, version: ${proposal.version}, status: ${proposal.status}" }

        // Verify owner
        if (proposal.ownerId != ownerId) {
            throw UnauthorizedOperationException("Only the proposal owner can revert proposals")
        }

        if (proposal.status != ProposalStatus.PUBLISHED) {
            throw InvalidOperationException("Only published proposals can be reverted")
        }

        // Revert in Git
        val revertResult = gitService.revertProposal(proposalId, proposal.version)

        if (!revertResult.success) {
            logger.error { "[PROPOSAL-CHANGE] Git revert failed - proposalId: $proposalId, message: ${revertResult.message}" }
            throw InvalidOperationException(revertResult.message)
        }

        // If this is the first version, just mark as DRAFT
        if (revertResult.previousVersion == null || revertResult.proposalData == null) {
            logger.info { "[PROPOSAL-CHANGE] Reverting to initial state - proposalId: $proposalId, marking as DRAFT" }
            proposal.status = ProposalStatus.DRAFT
            proposal.updatedAt = LocalDateTime.now()
            val updatedProposal = proposalRepository.save(proposal)

            // Remove from view manager index
            try {
                proposalViewManagerClient.deleteProposal(proposalId.toString())
                logger.info { "[PROPOSAL-CHANGE] Removed from view manager - proposalId: $proposalId" }
            } catch (e: Exception) {
                logger.error(e) { "[PROPOSAL-CHANGE] Failed to remove from view manager - proposalId: $proposalId, error: ${e.message}" }
            }

            val duration = System.currentTimeMillis() - startTime
            logger.info { "[PROPOSAL-CHANGE] Proposal reverted to DRAFT - proposalId: $proposalId, duration: ${duration}ms" }

            return toProposalResponse(updatedProposal)
        }

        // Update proposal with previous version data
        val previousData = revertResult.proposalData
        val oldVersion = proposal.version
        proposal.title = previousData.title
        proposal.content = previousData.content
        proposal.version = previousData.version
        proposal.gitCommitHash = previousData.commitHash
        proposal.status = ProposalStatus.PUBLISHED
        proposal.updatedAt = LocalDateTime.now()
        val revertedProposal = proposalRepository.save(proposal)

        logger.info { "[PROPOSAL-CHANGE] Proposal reverted - proposalId: $proposalId, fromVersion: $oldVersion, toVersion: ${previousData.version}" }

        // Publish previous version to view manager
        try {
            val publishDto = com.intrigsoft.ipitch.proposalmanager.client.ProposalPublishDto(
                id = revertedProposal.id!!,
                title = revertedProposal.title,
                content = revertedProposal.content,
                ownerId = revertedProposal.ownerId,
                ownerName = "User-${revertedProposal.ownerId}",
                contributors = revertedProposal.contributors.map { contributor ->
                    com.intrigsoft.ipitch.proposalmanager.client.ContributorDto(
                        id = contributor.id!!,
                        userId = contributor.userId,
                        userName = "User-${contributor.userId}",
                        role = contributor.role,
                        status = contributor.status.name
                    )
                },
                version = revertedProposal.version,
                status = revertedProposal.status.name,
                stats = revertedProposal.stats,
                workingBranch = revertedProposal.workingBranch,
                gitCommitHash = revertedProposal.gitCommitHash,
                createdAt = revertedProposal.createdAt,
                updatedAt = revertedProposal.updatedAt
            )
            proposalViewManagerClient.publishProposal(publishDto)
            logger.info { "[PROPOSAL-CHANGE] Reverted proposal indexed in view manager - proposalId: $proposalId, version: ${previousData.version}" }
        } catch (e: Exception) {
            logger.error(e) { "[PROPOSAL-CHANGE] Failed to index reverted proposal in view manager - proposalId: $proposalId, error: ${e.message}" }
        }

        val totalDuration = System.currentTimeMillis() - startTime
        logger.info { "[PROPOSAL-CHANGE] Proposal revert completed - proposalId: $proposalId, version: ${previousData.version}, duration: ${totalDuration}ms" }

        return toProposalResponse(revertedProposal)
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

    /**
     * Helper: Get or create user for git attribution
     * Creates a basic user profile if the user doesn't exist yet
     */
    private fun getOrCreateUser(userId: String): User {
        return userRepository.findById(userId).orElseGet {
            logger.info { "User $userId not found, creating basic profile for git attribution" }
            val user = User(
                userId = userId,
                userName = "User-$userId",
                email = "$userId@ipitch.com",
                status = UserStatus.ACTIVE
            )
            userRepository.save(user)
        }
    }

    /**
     * Marks all contributors (including owner) as dirty when a proposal is published
     */
    private fun markContributorsAsDirty(proposal: Proposal) {
        try {
            // Collect all user IDs (owner + contributors)
            val userIds = mutableSetOf<String>()
            userIds.add(proposal.ownerId)
            proposal.contributors.forEach { contributor ->
                userIds.add(contributor.userId)
            }

            // Mark all users as dirty
            userIds.forEach { userId ->
                userRepository.findById(userId).ifPresent { user ->
                    user.dirty = true
                    userRepository.save(user)
                    logger.info { "Marked user $userId as dirty after proposal ${proposal.id} was published" }
                }
            }

            logger.info { "Marked ${userIds.size} contributors as dirty for proposal ${proposal.id}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to mark contributors as dirty for proposal ${proposal.id}, but proposal was published successfully" }
            // Don't fail the publication if marking dirty fails
        }
    }

    /**
     * Get all suggestions for a proposal
     */
    @Transactional(readOnly = true)
    fun getSuggestionsForProposal(proposalId: UUID): List<SuggestionResponse> {
        logger.debug { "Fetching suggestions for proposal $proposalId" }

        // Verify proposal exists
        proposalRepository.findById(proposalId).orElseThrow {
            throw ProposalNotFoundException(proposalId)
        }

        return suggestionConcernService?.getSuggestionsForProposal(proposalId)
            ?.map { SuggestionResponse.from(it) }
            ?: emptyList()
    }

    /**
     * Get all concerns for a proposal
     */
    @Transactional(readOnly = true)
    fun getConcernsForProposal(proposalId: UUID): List<ConcernResponse> {
        logger.debug { "Fetching concerns for proposal $proposalId" }

        // Verify proposal exists
        proposalRepository.findById(proposalId).orElseThrow {
            throw ProposalNotFoundException(proposalId)
        }

        return suggestionConcernService?.getConcernsForProposal(proposalId)
            ?.map { ConcernResponse.from(it) }
            ?: emptyList()
    }
}
