package com.intrigsoft.ipitch.proposalviewmanager.repository

import com.intrigsoft.ipitch.proposalviewmanager.document.ProposalDocument
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository

/**
 * Repository for ProposalDocument in Elasticsearch
 */
@Repository
interface ProposalDocumentRepository : ElasticsearchRepository<ProposalDocument, String> {

    /**
     * Search proposals by title or content
     */
    fun findByTitleContainingOrContentContaining(
        title: String,
        content: String,
        pageable: Pageable
    ): Page<ProposalDocument>

    /**
     * Find proposals by owner ID
     */
    fun findByOwnerId(ownerId: String, pageable: Pageable): Page<ProposalDocument>

    /**
     * Find proposals by status
     */
    fun findByStatus(status: String, pageable: Pageable): Page<ProposalDocument>

    /**
     * Find proposals by owner ID and status
     */
    fun findByOwnerIdAndStatus(ownerId: String, status: String, pageable: Pageable): Page<ProposalDocument>
}
