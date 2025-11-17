package com.intrigsoft.ipitch.interactionmanager.document

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDateTime

@Document(indexName = "inferred_entities")
data class InferredEntityDocument(
    @Id
    val id: String,

    @Field(type = FieldType.Keyword)
    val proposalId: String,

    @Field(type = FieldType.Keyword)
    val sourceCommentId: String,

    @Field(type = FieldType.Keyword)
    val entityType: String,

    @Field(type = FieldType.Text, analyzer = "standard")
    val content: String,

    @Field(type = FieldType.Text, analyzer = "standard")
    val summary: String,

    @Field(type = FieldType.Keyword)
    val status: String,

    @Field(type = FieldType.Double)
    val confidenceScore: Double,

    @Field(type = FieldType.Long)
    val upvotes: Long = 0,

    @Field(type = FieldType.Long)
    val downvotes: Long = 0,

    @Field(type = FieldType.Long)
    val voteScore: Long = 0,

    @Field(type = FieldType.Long)
    val commentCount: Long = 0,

    @Field(type = FieldType.Date)
    val createdAt: LocalDateTime,

    @Field(type = FieldType.Date)
    val updatedAt: LocalDateTime,

    @Field(type = FieldType.Keyword)
    val reviewedBy: String?,

    @Field(type = FieldType.Date)
    val reviewedAt: LocalDateTime?
)
