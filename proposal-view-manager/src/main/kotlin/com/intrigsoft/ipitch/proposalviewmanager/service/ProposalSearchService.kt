package com.intrigsoft.ipitch.proposalviewmanager.service

import com.intrigsoft.ipitch.domain.ProposalStatus
import com.intrigsoft.ipitch.proposalviewmanager.document.ProposalDocument
import com.intrigsoft.ipitch.proposalviewmanager.dto.*
import com.intrigsoft.ipitch.proposalviewmanager.repository.ProposalDocumentRepository
import mu.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.SearchHit
import org.springframework.data.elasticsearch.core.query.Query
import org.springframework.stereotype.Service
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * Service for searching proposals in Elasticsearch
 */
@Service
class ProposalSearchService(
    private val proposalDocumentRepository: ProposalDocumentRepository,
    private val elasticsearchOperations: ElasticsearchOperations
) {

    /**
     * Search proposals with advanced filtering
     */
    fun searchProposals(request: ProposalSearchRequest): PagedProposalSearchResponse {
        logger.info { "Searching proposals with request: $request" }

        try {
            val pageable = PageRequest.of(
                request.page,
                request.size,
                if (request.sortOrder.equals("asc", ignoreCase = true))
                    Sort.by(request.sortBy).ascending()
                else
                    Sort.by(request.sortBy).descending()
            )

            val page: Page<ProposalDocument> = when {
                // Search by query text
                !request.query.isNullOrBlank() && request.ownerId == null && request.status == null -> {
                    proposalDocumentRepository.findByTitleContainingOrContentContaining(
                        request.query,
                        request.query,
                        pageable
                    )
                }
                // Search by owner and status
                request.ownerId != null && request.status != null && request.query.isNullOrBlank() -> {
                    proposalDocumentRepository.findByOwnerIdAndStatus(
                        request.ownerId,
                        request.status.name,
                        pageable
                    )
                }
                // Search by owner only
                request.ownerId != null && request.status == null && request.query.isNullOrBlank() -> {
                    proposalDocumentRepository.findByOwnerId(request.ownerId, pageable)
                }
                // Search by status only
                request.status != null && request.ownerId == null && request.query.isNullOrBlank() -> {
                    proposalDocumentRepository.findByStatus(request.status.name, pageable)
                }
                // Get all
                else -> {
                    proposalDocumentRepository.findAll(pageable)
                }
            }

            val searchResponses = page.content.map { doc ->
                ProposalSearchResponse(
                    id = UUID.fromString(doc.id),
                    title = doc.title,
                    content = doc.content,
                    ownerId = doc.ownerId,
                    ownerName = doc.ownerName,
                    contributors = doc.contributors.map { contributor ->
                        ProposalSearchResponse.ContributorDto(
                            id = UUID.fromString(contributor.id),
                            userId = contributor.userId,
                            userName = contributor.userName,
                            role = contributor.role,
                            status = contributor.status
                        )
                    },
                    version = doc.version,
                    status = ProposalStatus.valueOf(doc.status),
                    stats = doc.stats,
                    createdAt = doc.createdAt,
                    updatedAt = doc.updatedAt
                )
            }

            return PagedProposalSearchResponse(
                content = searchResponses,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                page = page.number,
                size = page.size
            )
        } catch (e: Exception) {
            logger.error(e) { "Error searching proposals" }
            throw e
        }
    }

    /**
     * Get a single proposal by ID
     */
    fun getProposalById(proposalId: String): ProposalSearchResponse? {
        logger.info { "Getting proposal by ID: $proposalId" }

        try {
            val document = proposalDocumentRepository.findById(proposalId).orElse(null)
                ?: return null

            return ProposalSearchResponse(
                id = UUID.fromString(document.id),
                title = document.title,
                content = document.content,
                ownerId = document.ownerId,
                ownerName = document.ownerName,
                contributors = document.contributors.map { contributor ->
                    ProposalSearchResponse.ContributorDto(
                        id = UUID.fromString(contributor.id),
                        userId = contributor.userId,
                        userName = contributor.userName,
                        role = contributor.role,
                        status = contributor.status
                    )
                },
                version = document.version,
                status = ProposalStatus.valueOf(document.status),
                stats = document.stats,
                createdAt = document.createdAt,
                updatedAt = document.updatedAt
            )
        } catch (e: Exception) {
            logger.error(e) { "Error getting proposal: $proposalId" }
            throw e
        }
    }
}
