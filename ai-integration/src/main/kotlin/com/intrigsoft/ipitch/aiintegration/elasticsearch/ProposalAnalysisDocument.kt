package com.intrigsoft.ipitch.aiintegration.elasticsearch

import com.intrigsoft.ipitch.aiintegration.model.AIProvider
import com.intrigsoft.ipitch.aiintegration.model.SectorScore
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.*
import java.time.Instant
import java.util.UUID

/**
 * Elasticsearch document for proposal analysis results
 * Enables search and user behavior analytics
 */
@Document(indexName = "proposal-analysis")
@Setting(settingPath = "elasticsearch/settings.json")
data class ProposalAnalysisDocument(
    @Id
    @Field(type = FieldType.Keyword)
    val id: String,

    @Field(type = FieldType.Keyword)
    val proposalId: String,

    @Field(type = FieldType.Keyword)
    val ownerId: String,  // For user analytics

    @Field(type = FieldType.Text, analyzer = "standard")
    val proposalTitle: String,

    @Field(type = FieldType.Text, analyzer = "standard")
    val proposalContent: String,

    @Field(type = FieldType.Text, analyzer = "standard")
    val summary: String,

    @Field(type = FieldType.Double)
    val clarityScore: Double,

    @Field(type = FieldType.Nested)
    val sectorScores: List<SectorScoreES>,

    @Field(type = FieldType.Keyword)
    val embeddingId: String?,

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
            analysis: com.intrigsoft.ipitch.aiintegration.model.ProposalAnalysis,
            proposal: com.intrigsoft.ipitch.domain.Proposal
        ): ProposalAnalysisDocument {
            return ProposalAnalysisDocument(
                id = analysis.id.toString(),
                proposalId = analysis.proposalId.toString(),
                ownerId = proposal.ownerId.toString(),
                proposalTitle = proposal.title,
                proposalContent = proposal.content,
                summary = analysis.summary,
                clarityScore = analysis.clarityScore,
                sectorScores = analysis.sectorScores.map { SectorScoreES(it.sector, it.score) },
                embeddingId = analysis.embeddingId?.toString(),
                model = analysis.model,
                provider = analysis.provider.name,
                analyzedAt = analysis.analyzedAt,
                createdAt = analysis.createdAt
            )
        }
    }
}

/**
 * Elasticsearch-compatible sector score (nested object)
 */
data class SectorScoreES(
    @Field(type = FieldType.Keyword)
    val sector: String,

    @Field(type = FieldType.Double)
    val score: Double
)
