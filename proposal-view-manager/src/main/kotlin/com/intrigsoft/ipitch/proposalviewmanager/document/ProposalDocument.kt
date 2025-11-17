package com.intrigsoft.ipitch.proposalviewmanager.document

import com.intrigsoft.ipitch.domain.ProposalStatus
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDateTime
import java.util.*

/**
 * Elasticsearch document for Proposal
 */
@Document(indexName = "proposals")
data class ProposalDocument(
    @Id
    val id: String,

    @Field(type = FieldType.Text, analyzer = "standard")
    val title: String,

    @Field(type = FieldType.Text, analyzer = "standard")
    val content: String,

    @Field(type = FieldType.Keyword)
    val ownerId: String,

    @Field(type = FieldType.Text)
    val ownerName: String? = null,

    @Field(type = FieldType.Nested)
    val contributors: List<ContributorDocument> = emptyList(),

    @Field(type = FieldType.Keyword)
    val version: String,

    @Field(type = FieldType.Keyword)
    val status: String,

    @Field(type = FieldType.Object)
    val stats: Map<String, Any> = emptyMap(),

    @Field(type = FieldType.Keyword)
    val workingBranch: String? = null,

    @Field(type = FieldType.Keyword)
    val gitCommitHash: String? = null,

    @Field(type = FieldType.Date)
    val createdAt: LocalDateTime,

    @Field(type = FieldType.Date)
    val updatedAt: LocalDateTime
)

data class ContributorDocument(
    val id: String,
    val userId: String,
    val userName: String? = null,
    val role: String,
    val status: String
)
