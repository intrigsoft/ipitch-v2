package com.intrigsoft.ipitch.interactionmanager.search

import com.intrigsoft.ipitch.interactionmanager.document.InferredEntityDocument
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository

@Repository
interface InferredEntitySearchRepository : ElasticsearchRepository<InferredEntityDocument, String> {
    fun findByProposalIdOrderByVoteScoreDesc(proposalId: String): List<InferredEntityDocument>

    fun findByEntityTypeAndStatusOrderByVoteScoreDesc(
        entityType: String,
        status: String
    ): List<InferredEntityDocument>

    fun findByContentContainingIgnoreCaseOrSummaryContainingIgnoreCase(
        content: String,
        summary: String
    ): List<InferredEntityDocument>
}
