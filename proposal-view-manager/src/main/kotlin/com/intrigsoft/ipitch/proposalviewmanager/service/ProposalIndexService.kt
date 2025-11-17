package com.intrigsoft.ipitch.proposalviewmanager.service

import com.intrigsoft.ipitch.proposalviewmanager.document.ContributorDocument
import com.intrigsoft.ipitch.proposalviewmanager.document.ProposalDocument
import com.intrigsoft.ipitch.proposalviewmanager.dto.ProposalPublishDto
import com.intrigsoft.ipitch.proposalviewmanager.repository.ProposalDocumentRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Service for indexing proposals in Elasticsearch
 */
@Service
class ProposalIndexService(
    private val proposalDocumentRepository: ProposalDocumentRepository
) {

    /**
     * Index or update a proposal in Elasticsearch
     * Note: Using proposal UUID as document ID ensures only one version exists.
     * When a new version is published, it replaces the previous version in the index.
     */
    fun indexProposal(publishDto: ProposalPublishDto) {
        logger.info { "Indexing proposal: ${publishDto.id}, version: ${publishDto.version}" }

        try {
            val proposalDocument = ProposalDocument(
                id = publishDto.id.toString(),
                title = publishDto.title,
                content = publishDto.content,
                ownerId = publishDto.ownerId.toString(),
                ownerName = publishDto.ownerName,
                contributors = publishDto.contributors.map { contributor ->
                    ContributorDocument(
                        id = contributor.id.toString(),
                        userId = contributor.userId.toString(),
                        userName = contributor.userName,
                        role = contributor.role,
                        status = contributor.status
                    )
                },
                version = publishDto.version,
                status = publishDto.status.name,
                stats = publishDto.stats,
                workingBranch = publishDto.workingBranch,
                gitCommitHash = publishDto.gitCommitHash,
                createdAt = publishDto.createdAt,
                updatedAt = publishDto.updatedAt
            )

            proposalDocumentRepository.save(proposalDocument)
            logger.info { "Successfully indexed proposal: ${publishDto.id}" }
        } catch (e: Exception) {
            logger.error(e) { "Error indexing proposal: ${publishDto.id}" }
            throw e
        }
    }

    /**
     * Delete a proposal from Elasticsearch
     */
    fun deleteProposal(proposalId: String) {
        logger.info { "Deleting proposal from index: $proposalId" }

        try {
            proposalDocumentRepository.deleteById(proposalId)
            logger.info { "Successfully deleted proposal: $proposalId" }
        } catch (e: Exception) {
            logger.error(e) { "Error deleting proposal: $proposalId" }
            throw e
        }
    }
}
