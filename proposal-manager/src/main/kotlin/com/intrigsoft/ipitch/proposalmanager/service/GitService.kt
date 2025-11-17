package com.intrigsoft.ipitch.proposalmanager.service

import com.intrigsoft.ipitch.proposalmanager.config.GitProperties
import mu.KotlinLogging
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.RefAlreadyExistsException
import org.eclipse.jgit.merge.MergeStrategy
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import jakarta.annotation.PostConstruct

private val logger = KotlinLogging.logger {}

@Service
class GitService(
    private val gitProperties: GitProperties
) {
    private lateinit var git: Git
    private lateinit var repoPath: Path

    @PostConstruct
    fun initializeRepository() {
        logger.info { "Initializing Git repository at ${gitProperties.repositoryPath}" }

        repoPath = Paths.get(gitProperties.repositoryPath)
        val repoDir = repoPath.toFile()

        if (!repoDir.exists()) {
            logger.info { "Creating new Git repository at ${gitProperties.repositoryPath}" }
            repoDir.mkdirs()
            git = Git.init()
                .setDirectory(repoDir)
                .setInitialBranch(gitProperties.mainBranch)
                .call()

            // Create initial commit
            val readmeFile = File(repoDir, "README.md")
            readmeFile.writeText("# Proposal Repository\n\nThis repository contains all proposals managed by the iPitch system.")
            git.add().addFilepattern("README.md").call()
            git.commit()
                .setMessage("Initial commit")
                .setAuthor("System", "system@ipitch.com")
                .call()

            logger.info { "Git repository initialized successfully" }
        } else {
            logger.info { "Opening existing Git repository at ${gitProperties.repositoryPath}" }
            git = Git.open(repoDir)
        }
    }

    /**
     * Creates a new proposal directory and working branch
     */
    fun createProposal(proposalId: UUID, title: String, content: String): String {
        logger.info { "Creating proposal $proposalId in Git repository" }

        val branchName = "proposal/$proposalId"
        val proposalDir = File(repoPath.toFile(), proposalId.toString())

        try {
            // Ensure we're on main branch
            checkoutBranch(gitProperties.mainBranch)

            // Create proposal directory
            proposalDir.mkdirs()
            logger.debug { "Created proposal directory: ${proposalDir.absolutePath}" }

            // Create proposal files
            val metadataFile = File(proposalDir, "metadata.json")
            metadataFile.writeText("""
                {
                  "proposalId": "$proposalId",
                  "title": "$title"
                }
            """.trimIndent())

            val contentFile = File(proposalDir, "content.md")
            contentFile.writeText(content)

            logger.debug { "Created proposal files in ${proposalDir.absolutePath}" }

            // Add and commit files
            git.add()
                .addFilepattern("$proposalId/metadata.json")
                .addFilepattern("$proposalId/content.md")
                .call()

            val commit = git.commit()
                .setMessage("Create proposal: $title")
                .setAuthor("System", "system@ipitch.com")
                .call()

            logger.info { "Created initial commit for proposal $proposalId: ${commit.name}" }

            // Create working branch
            git.branchCreate()
                .setName(branchName)
                .call()

            logger.info { "Created working branch: $branchName" }

            return branchName
        } catch (e: Exception) {
            logger.error(e) { "Error creating proposal $proposalId in Git" }
            throw RuntimeException("Failed to create proposal in Git repository", e)
        }
    }

    /**
     * Creates a contributor branch from the proposal's working branch
     */
    fun createContributorBranch(proposalId: UUID, contributorId: UUID): String {
        logger.info { "Creating contributor branch for proposal $proposalId, contributor $contributorId" }

        val workingBranch = "proposal/$proposalId"
        val contributorBranch = "proposal/$proposalId/contributor/$contributorId"

        try {
            // Checkout working branch first
            checkoutBranch(workingBranch)

            // Create contributor branch from working branch
            git.branchCreate()
                .setName(contributorBranch)
                .setStartPoint(workingBranch)
                .call()

            logger.info { "Created contributor branch: $contributorBranch" }

            return contributorBranch
        } catch (e: RefAlreadyExistsException) {
            logger.warn { "Contributor branch already exists: $contributorBranch" }
            return contributorBranch
        } catch (e: Exception) {
            logger.error(e) { "Error creating contributor branch" }
            throw RuntimeException("Failed to create contributor branch", e)
        }
    }

    /**
     * Updates proposal content on a contributor's branch
     */
    fun updateContent(proposalId: UUID, contributorId: UUID, content: String, commitMessage: String, authorName: String, authorEmail: String): String {
        logger.info { "Updating content for proposal $proposalId by contributor $contributorId" }

        val contributorBranch = "proposal/$proposalId/contributor/$contributorId"

        try {
            checkoutBranch(contributorBranch)

            val proposalDir = File(repoPath.toFile(), proposalId.toString())
            val contentFile = File(proposalDir, "content.md")
            contentFile.writeText(content)

            logger.debug { "Updated content file: ${contentFile.absolutePath}" }

            git.add()
                .addFilepattern("$proposalId/content.md")
                .call()

            val commit = git.commit()
                .setMessage(commitMessage)
                .setAuthor(authorName, authorEmail)
                .call()

            logger.info { "Committed content update: ${commit.name}" }

            return commit.name
        } catch (e: Exception) {
            logger.error(e) { "Error updating content" }
            throw RuntimeException("Failed to update content in Git repository", e)
        }
    }

    /**
     * Updates proposal title on a contributor's branch
     */
    fun updateTitle(proposalId: UUID, contributorId: UUID, title: String, commitMessage: String, authorName: String, authorEmail: String): String {
        logger.info { "Updating title for proposal $proposalId by contributor $contributorId" }

        val contributorBranch = "proposal/$proposalId/contributor/$contributorId"

        try {
            checkoutBranch(contributorBranch)

            val proposalDir = File(repoPath.toFile(), proposalId.toString())
            val metadataFile = File(proposalDir, "metadata.json")

            // Read existing metadata and update title
            val metadata = """
                {
                  "proposalId": "$proposalId",
                  "title": "$title"
                }
            """.trimIndent()

            metadataFile.writeText(metadata)

            logger.debug { "Updated metadata file: ${metadataFile.absolutePath}" }

            git.add()
                .addFilepattern("$proposalId/metadata.json")
                .call()

            val commit = git.commit()
                .setMessage(commitMessage)
                .setAuthor(authorName, authorEmail)
                .call()

            logger.info { "Committed title update: ${commit.name}" }

            return commit.name
        } catch (e: Exception) {
            logger.error(e) { "Error updating title" }
            throw RuntimeException("Failed to update title in Git repository", e)
        }
    }

    /**
     * Simulates creating a pull request (stores PR info in a file)
     * In a real system, this would integrate with a Git platform like GitHub/GitLab
     */
    fun createPullRequest(proposalId: UUID, contributorId: UUID, description: String): String {
        logger.info { "Creating pull request for proposal $proposalId from contributor $contributorId" }

        val sourceBranch = "proposal/$proposalId/contributor/$contributorId"
        val targetBranch = "proposal/$proposalId"
        val prId = UUID.randomUUID().toString()

        try {
            // Store PR information
            val prDir = File(repoPath.toFile(), ".pull-requests")
            prDir.mkdirs()

            val prFile = File(prDir, "$prId.json")
            val prData = """
                {
                  "id": "$prId",
                  "proposalId": "$proposalId",
                  "contributorId": "$contributorId",
                  "sourceBranch": "$sourceBranch",
                  "targetBranch": "$targetBranch",
                  "description": "$description",
                  "status": "OPEN",
                  "createdAt": "${java.time.LocalDateTime.now()}"
                }
            """.trimIndent()

            prFile.writeText(prData)

            logger.info { "Created pull request: $prId from $sourceBranch to $targetBranch" }

            return prId
        } catch (e: Exception) {
            logger.error(e) { "Error creating pull request" }
            throw RuntimeException("Failed to create pull request", e)
        }
    }

    /**
     * Merges a pull request (contributor branch to proposal working branch)
     */
    fun mergePullRequest(pullRequestId: String, commitMessage: String?): String {
        logger.info { "Merging pull request $pullRequestId" }

        try {
            // Read PR information
            val prFile = File(repoPath.toFile(), ".pull-requests/$pullRequestId.json")
            if (!prFile.exists()) {
                throw IllegalArgumentException("Pull request not found: $pullRequestId")
            }

            // Parse PR data (simple string parsing for now)
            val prContent = prFile.readText()
            val sourceBranch = extractJsonValue(prContent, "sourceBranch")
            val targetBranch = extractJsonValue(prContent, "targetBranch")

            logger.info { "Merging $sourceBranch into $targetBranch" }

            // Checkout target branch
            checkoutBranch(targetBranch)

            // Merge source branch
            val mergeResult = git.merge()
                .include(git.repository.resolve(sourceBranch))
                .setStrategy(MergeStrategy.RECURSIVE)
                .setCommit(true)
                .setMessage(commitMessage ?: "Merge pull request $pullRequestId")
                .call()

            if (mergeResult.mergeStatus.isSuccessful) {
                logger.info { "Pull request merged successfully: ${mergeResult.newHead.name}" }

                // Update PR status
                val updatedPrData = prContent.replace("\"status\": \"OPEN\"", "\"status\": \"MERGED\"")
                prFile.writeText(updatedPrData)

                return mergeResult.newHead.name
            } else {
                logger.error { "Merge failed: ${mergeResult.mergeStatus}" }
                throw RuntimeException("Merge conflict detected. Please resolve manually. Status: ${mergeResult.mergeStatus}")
            }
        } catch (e: Exception) {
            logger.error(e) { "Error merging pull request" }
            throw RuntimeException("Failed to merge pull request", e)
        }
    }

    /**
     * Publishes a proposal by merging to main branch and creating a version tag
     */
    fun publishProposal(proposalId: UUID, version: String): String {
        logger.info { "Publishing proposal $proposalId with version $version" }

        val workingBranch = "proposal/$proposalId"
        val tagName = "$proposalId-$version"

        try {
            // Checkout main branch
            checkoutBranch(gitProperties.mainBranch)

            // Merge working branch to main
            val mergeResult = git.merge()
                .include(git.repository.resolve(workingBranch))
                .setStrategy(MergeStrategy.RECURSIVE)
                .setCommit(true)
                .setMessage("Publish proposal $proposalId version $version")
                .call()

            if (!mergeResult.mergeStatus.isSuccessful) {
                logger.error { "Publish merge failed: ${mergeResult.mergeStatus}" }
                throw RuntimeException("Failed to merge proposal to main branch. Status: ${mergeResult.mergeStatus}")
            }

            logger.info { "Merged proposal to main branch: ${mergeResult.newHead.name}" }

            // Create version tag
            git.tag()
                .setName(tagName)
                .setMessage("Version $version of proposal $proposalId")
                .call()

            logger.info { "Created version tag: $tagName" }

            return mergeResult.newHead.name
        } catch (e: Exception) {
            logger.error(e) { "Error publishing proposal" }
            throw RuntimeException("Failed to publish proposal", e)
        }
    }

    /**
     * Checks out a branch
     */
    private fun checkoutBranch(branchName: String) {
        logger.debug { "Checking out branch: $branchName" }
        git.checkout()
            .setName(branchName)
            .call()
    }

    /**
     * Simple JSON value extractor (for PR data)
     */
    private fun extractJsonValue(json: String, key: String): String {
        val pattern = """"$key":\s*"([^"]*)"""".toRegex()
        val matchResult = pattern.find(json)
        return matchResult?.groupValues?.get(1) ?: throw IllegalArgumentException("Key not found: $key")
    }

    fun close() {
        git.close()
    }
}
