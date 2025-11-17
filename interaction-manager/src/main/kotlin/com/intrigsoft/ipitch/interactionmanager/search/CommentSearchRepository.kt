package com.intrigsoft.ipitch.interactionmanager.search

import com.intrigsoft.ipitch.interactionmanager.document.CommentDocument
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository

@Repository
interface CommentSearchRepository : ElasticsearchRepository<CommentDocument, String> {
    fun findByTargetTypeAndTargetIdOrderByVoteScoreDesc(
        targetType: String,
        targetId: String
    ): List<CommentDocument>

    fun findByContentContainingIgnoreCase(content: String): List<CommentDocument>
}
