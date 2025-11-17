package com.intrigsoft.ipitch.aiintegration.elasticsearch

import com.intrigsoft.ipitch.aiintegration.model.ContentMode
import com.intrigsoft.ipitch.aiintegration.model.GovernanceFlag
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.*
import java.time.Instant

/**
 * Elasticsearch document for comment analysis results
 * Enables search, moderation tracking, and user behavior analytics
 */
@Document(indexName = "comment-analysis")
@Setting(settingPath = "elasticsearch/settings.json")
data class CommentAnalysisDocument(
    @Id
    @Field(type = FieldType.Keyword)
    val id: String,

    @Field(type = FieldType.Keyword)
    val commentId: String,

    @Field(type = FieldType.Keyword)
    val userId: String,  // For user analytics

    @Field(type = FieldType.Keyword)
    val proposalId: String?,  // Root proposal for context

    @Field(type = FieldType.Text, analyzer = "standard")
    val commentContent: String,

    // Governance/Moderation fields
    @Field(type = FieldType.Keyword)
    val governanceFlags: List<String>,

    @Field(type = FieldType.Double)
    val governanceScore: Double,

    @Field(type = FieldType.Boolean)
    val isFlagged: Boolean,

    @Field(type = FieldType.Text)
    val flagReason: String?,

    // Content analysis fields
    @Field(type = FieldType.Double)
    val relevanceScore: Double?,

    @Field(type = FieldType.Nested)
    val sectorScores: List<SectorScoreES>?,

    @Field(type = FieldType.Keyword)
    val mode: String?,  // SUPPORTIVE, CRITICAL, NEUTRAL, INQUISITIVE, SUGGESTIVE

    @Field(type = FieldType.Boolean)
    val isMarketing: Boolean,

    @Field(type = FieldType.Double)
    val marketingScore: Double?,

    @Field(type = FieldType.Keyword)
    val model: String,

    @Field(type = FieldType.Keyword)
    val provider: String,

    @Field(type = FieldType.Date, format = [DateFormat.date_time])
    val analyzedAt: Instant,

    @Field(type = FieldType.Date, format = [DateFormat.date_time])
    val createdAt: Instant
) {
    companion object {
        fun from(
            analysis: com.intrigsoft.ipitch.aiintegration.model.CommentAnalysis,
            comment: com.intrigsoft.ipitch.domain.Comment,
            proposalId: String?
        ): CommentAnalysisDocument {
            return CommentAnalysisDocument(
                id = analysis.id.toString(),
                commentId = analysis.commentId.toString(),
                userId = comment.userId.toString(),
                proposalId = proposalId,
                commentContent = comment.content,
                governanceFlags = analysis.governanceFlags.map { it.name },
                governanceScore = analysis.governanceScore,
                isFlagged = analysis.isFlagged,
                flagReason = analysis.flagReason,
                relevanceScore = analysis.relevanceScore,
                sectorScores = analysis.sectorScores?.map { SectorScoreES(it.sector, it.score) },
                mode = analysis.mode?.name,
                isMarketing = analysis.isMarketing,
                marketingScore = analysis.marketingScore,
                model = analysis.model,
                provider = analysis.provider.name,
                analyzedAt = analysis.analyzedAt,
                createdAt = analysis.createdAt
            )
        }
    }
}
