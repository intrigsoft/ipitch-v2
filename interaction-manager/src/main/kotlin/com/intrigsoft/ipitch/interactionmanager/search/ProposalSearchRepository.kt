package com.intrigsoft.ipitch.interactionmanager.search

import com.intrigsoft.ipitch.interactionmanager.document.ProposalDocument
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository

@Repository
interface ProposalSearchRepository : ElasticsearchRepository<ProposalDocument, String> {
    fun findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
        title: String,
        content: String
    ): List<ProposalDocument>

    fun findByStatusOrderByVoteScoreDesc(status: String): List<ProposalDocument>

    fun findAllByOrderByVoteScoreDesc(): List<ProposalDocument>
}
