package com.intrigsoft.ipitch.aiintegration.service

import com.intrigsoft.ipitch.aiintegration.elasticsearch.*
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.util.*

class UserAnalyticsServiceTest {

    private lateinit var proposalAnalysisRepository: ProposalAnalysisElasticsearchRepository
    private lateinit var commentAnalysisRepository: CommentAnalysisElasticsearchRepository
    private lateinit var userAnalyticsService: UserAnalyticsService

    @BeforeEach
    fun setup() {
        proposalAnalysisRepository = mockk()
        commentAnalysisRepository = mockk()
        userAnalyticsService = UserAnalyticsService(
            proposalAnalysisRepository,
            commentAnalysisRepository
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `getUserProposalMetrics should return empty metrics for user with no proposals`() {
        // Given
        val userId = UUID.randomUUID()
        every {
            proposalAnalysisRepository.findByOwnerId(userId.toString(), any())
        } returns PageImpl(emptyList())

        // When
        val metrics = userAnalyticsService.getUserProposalMetrics(userId)

        // Then
        assertEquals(0, metrics.totalProposals)
        assertEquals(0.0, metrics.averageClarityScore)
        assertEquals(0, metrics.highQualityProposalCount)
        assertTrue(metrics.sectorExpertise.isEmpty())
    }

    @Test
    fun `getUserProposalMetrics should calculate average clarity score correctly`() {
        // Given
        val userId = UUID.randomUUID()
        val proposals = listOf(
            createProposalAnalysisDocument(userId, 8.0),
            createProposalAnalysisDocument(userId, 9.0),
            createProposalAnalysisDocument(userId, 7.0)
        )

        every {
            proposalAnalysisRepository.findByOwnerId(userId.toString(), any())
        } returns PageImpl(proposals)

        // When
        val metrics = userAnalyticsService.getUserProposalMetrics(userId)

        // Then
        assertEquals(3, metrics.totalProposals)
        assertEquals(8.0, metrics.averageClarityScore, 0.01)
        assertEquals(2, metrics.highQualityProposalCount)  // >= 7.5
    }

    @Test
    fun `getUserCommentMetrics should return empty metrics for user with no comments`() {
        // Given
        val userId = UUID.randomUUID()
        every {
            commentAnalysisRepository.findByUserId(userId.toString(), any())
        } returns PageImpl(emptyList())

        // When
        val metrics = userAnalyticsService.getUserCommentMetrics(userId)

        // Then
        assertEquals(0, metrics.totalComments)
        assertEquals(0, metrics.flaggedCommentCount)
        assertEquals(0.0, metrics.flaggedCommentPercentage)
    }

    @Test
    fun `getUserCommentMetrics should calculate flagged percentage correctly`() {
        // Given
        val userId = UUID.randomUUID()
        val comments = listOf(
            createCommentAnalysisDocument(userId, isFlagged = true),
            createCommentAnalysisDocument(userId, isFlagged = false),
            createCommentAnalysisDocument(userId, isFlagged = false),
            createCommentAnalysisDocument(userId, isFlagged = false)
        )

        every {
            commentAnalysisRepository.findByUserId(userId.toString(), any())
        } returns PageImpl(comments)

        // When
        val metrics = userAnalyticsService.getUserCommentMetrics(userId)

        // Then
        assertEquals(4, metrics.totalComments)
        assertEquals(1, metrics.flaggedCommentCount)
        assertEquals(25.0, metrics.flaggedCommentPercentage, 0.01)
    }

    @Test
    fun `getUserProfile should calculate aggressiveness score correctly`() {
        // Given
        val userId = UUID.randomUUID()

        // User with mostly critical comments
        val comments = listOf(
            createCommentAnalysisDocument(userId, mode = "CRITICAL"),
            createCommentAnalysisDocument(userId, mode = "CRITICAL"),
            createCommentAnalysisDocument(userId, mode = "SUPPORTIVE")
        )

        every {
            proposalAnalysisRepository.findByOwnerId(userId.toString(), any())
        } returns PageImpl(emptyList())

        every {
            commentAnalysisRepository.findByUserId(userId.toString(), any())
        } returns PageImpl(comments)

        // When
        val profile = userAnalyticsService.getUserProfile(userId)

        // Then
        // 2 critical, 1 supportive = (2-1)/3 = 0.33 critical ratio
        // Aggressiveness should be > 5 (neutral) because more critical than supportive
        assertTrue(profile.aggressivenessScore > 5.0)
    }

    private fun createProposalAnalysisDocument(
        userId: UUID,
        clarityScore: Double
    ): ProposalAnalysisDocument {
        return ProposalAnalysisDocument(
            id = UUID.randomUUID().toString(),
            proposalId = UUID.randomUUID().toString(),
            ownerId = userId.toString(),
            proposalTitle = "Test Proposal",
            proposalContent = "Test content",
            summary = "Test summary",
            clarityScore = clarityScore,
            sectorScores = listOf(SectorScoreES("IT", 8.0)),
            embeddingId = null,
            model = "test-model",
            provider = "OPENAI",
            analyzedAt = Instant.now(),
            createdAt = Instant.now()
        )
    }

    private fun createCommentAnalysisDocument(
        userId: UUID,
        isFlagged: Boolean = false,
        mode: String? = "NEUTRAL"
    ): CommentAnalysisDocument {
        return CommentAnalysisDocument(
            id = UUID.randomUUID().toString(),
            commentId = UUID.randomUUID().toString(),
            userId = userId.toString(),
            proposalId = UUID.randomUUID().toString(),
            commentContent = "Test comment",
            governanceFlags = if (isFlagged) listOf("SPAM") else listOf("NONE"),
            governanceScore = if (isFlagged) 0.9 else 0.1,
            isFlagged = isFlagged,
            flagReason = if (isFlagged) "Test flag" else null,
            relevanceScore = 7.5,
            sectorScores = listOf(SectorScoreES("IT", 8.0)),
            mode = mode,
            isMarketing = false,
            marketingScore = 0.0,
            model = "test-model",
            provider = "OPENAI",
            analyzedAt = Instant.now(),
            createdAt = Instant.now()
        )
    }
}
