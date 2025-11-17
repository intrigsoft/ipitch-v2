package com.intrigsoft.ipitch.interactionmanager.document

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import java.time.LocalDateTime
import java.util.*

@Document(indexName = "comments")
data class CommentDocument(
    @Id
    val id: String,

    @Field(type = FieldType.Keyword)
    val userId: String,

    @Field(type = FieldType.Text, analyzer = "standard")
    val content: String,

    @Field(type = FieldType.Keyword)
    val parentCommentId: String?,

    @Field(type = FieldType.Keyword)
    val targetType: String,

    @Field(type = FieldType.Keyword)
    val targetId: String,

    @Field(type = FieldType.Long)
    val upvotes: Long = 0,

    @Field(type = FieldType.Long)
    val downvotes: Long = 0,

    @Field(type = FieldType.Long)
    val voteScore: Long = 0,

    @Field(type = FieldType.Long)
    val replyCount: Long = 0,

    @Field(type = FieldType.Date)
    val createdAt: LocalDateTime,

    @Field(type = FieldType.Date)
    val updatedAt: LocalDateTime,

    @Field(type = FieldType.Boolean)
    val deleted: Boolean = false
)
