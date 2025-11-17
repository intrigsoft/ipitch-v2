package com.intrigsoft.ipitch.aiintegration.elasticsearch

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.elasticsearch.annotations.Query
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import org.springframework.stereotype.Repository

/**
 * Elasticsearch repository for proposal analysis documents
 */
@Repository
interface ProposalAnalysisElasticsearchRepository : ElasticsearchRepository<ProposalAnalysisDocument, String> {

    /**
     * Find proposal analysis by proposal ID
     */
    fun findByProposalId(proposalId: String): ProposalAnalysisDocument?

    /**
     * Find all analyses by owner (for user analytics)
     */
    fun findByOwnerId(ownerId: String, pageable: Pageable): Page<ProposalAnalysisDocument>

    /**
     * Search proposals by summary content
     */
    @Query("""
        {
            "multi_match": {
                "query": "?0",
                "fields": ["summary^2", "proposalTitle^1.5", "proposalContent"]
            }
        }
    """)
    fun searchBySummary(query: String, pageable: Pageable): Page<ProposalAnalysisDocument>

    /**
     * Find proposals with clarity score in range
     */
    fun findByClarityScoreBetween(minScore: Double, maxScore: Double, pageable: Pageable): Page<ProposalAnalysisDocument>

    /**
     * Find proposals by owner with minimum clarity score (for user analytics)
     */
    fun findByOwnerIdAndClarityScoreGreaterThanEqual(
        ownerId: String,
        minClarityScore: Double,
        pageable: Pageable
    ): Page<ProposalAnalysisDocument>

    /**
     * Find all proposals for a specific sector (for sector analysis)
     * Uses nested query on sectorScores
     */
    @Query("""
        {
            "nested": {
                "path": "sectorScores",
                "query": {
                    "bool": {
                        "must": [
                            {"term": {"sectorScores.sector": "?0"}},
                            {"range": {"sectorScores.score": {"gte": ?1}}}
                        ]
                    }
                }
            }
        }
    """)
    fun findBySectorAndMinScore(sector: String, minScore: Double, pageable: Pageable): Page<ProposalAnalysisDocument>
}
