package com.intrigsoft.ipitch.interactionmanager.service

import com.intrigsoft.ipitch.domain.InferredEntity
import com.intrigsoft.ipitch.domain.InferredEntityStatus
import com.intrigsoft.ipitch.domain.InferredEntityType
import com.intrigsoft.ipitch.domain.VoteTargetType
import com.intrigsoft.ipitch.interactionmanager.dto.response.VoteStatsResponse
import com.intrigsoft.ipitch.interactionmanager.exception.InferredEntityNotFoundException
import com.intrigsoft.ipitch.repository.CommentRepository
import com.intrigsoft.ipitch.repository.InferredEntityRepository
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class InferredEntityServiceTest {

    @MockK
    private lateinit var inferredEntityRepository: InferredEntityRepository

    @MockK
    private lateinit var commentRepository: CommentRepository

    @MockK
    private lateinit var voteService: VoteService

    @MockK
    private lateinit var elasticsearchSyncService: ElasticsearchSyncService

    @InjectMockKs
    private lateinit var inferredEntityService: InferredEntityService

    private lateinit var testEntity: InferredEntity
    private lateinit var testUserId: String
    private lateinit var testProposalId: UUID
    private lateinit var testCommentId: UUID

    @BeforeEach
    fun setUp() {
        testUserId = "user-${UUID.randomUUID()}"
        testProposalId = UUID.randomUUID()
        testCommentId = UUID.randomUUID()
        testEntity = InferredEntity(
            id = UUID.randomUUID(),
            proposalId = testProposalId,
            sourceCommentId = testCommentId,
            entityType = InferredEntityType.SUGGESTION,
            content = "Test suggestion content",
            summary = "Test summary",
            status = InferredEntityStatus.PENDING,
            confidenceScore = 0.85,
            metadata = mapOf("keywords" to listOf("test", "suggestion")),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            reviewedBy = null,
            reviewedAt = null
        )
    }

    @Test
    fun `getInferredEntity should return entity when it exists`() {
        // Given
        val entityId = testEntity.id!!
        val voteStats = VoteStatsResponse(upvotes = 5, downvotes = 1, score = 4, userVote = "UP")

        every { inferredEntityRepository.findById(entityId) } returns Optional.of(testEntity)
        every { voteService.getVoteStats(VoteTargetType.INFERRED_ENTITY, entityId, testUserId) } returns voteStats
        every {
            commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(
                com.intrigsoft.ipitch.domain.CommentTargetType.INFERRED_ENTITY,
                entityId
            )
        } returns 2L

        // When
        val result = inferredEntityService.getInferredEntity(entityId, testUserId)

        // Then
        assertNotNull(result)
        assertEquals(entityId, result.id)
        assertEquals(InferredEntityType.SUGGESTION, result.entityType)
        assertEquals(4L, result.voteStats.score)
        assertEquals(2L, result.commentCount)
    }

    @Test
    fun `getInferredEntity should throw InferredEntityNotFoundException when entity does not exist`() {
        // Given
        val entityId = UUID.randomUUID()

        every { inferredEntityRepository.findById(entityId) } returns Optional.empty()

        // When & Then
        assertThrows<InferredEntityNotFoundException> {
            inferredEntityService.getInferredEntity(entityId, testUserId)
        }
    }

    @Test
    fun `getInferredEntitiesByProposal should return all entities for proposal`() {
        // Given
        val entities = listOf(
            testEntity,
            testEntity.copy(id = UUID.randomUUID(), entityType = InferredEntityType.CONCERN)
        )
        val voteStats = VoteStatsResponse(upvotes = 0, downvotes = 0, score = 0, userVote = null)

        every { inferredEntityRepository.findByProposalId(testProposalId) } returns entities
        every { voteService.getVoteStats(VoteTargetType.INFERRED_ENTITY, any(), testUserId) } returns voteStats
        every {
            commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(
                com.intrigsoft.ipitch.domain.CommentTargetType.INFERRED_ENTITY,
                any()
            )
        } returns 0L

        // When
        val result = inferredEntityService.getInferredEntitiesByProposal(testProposalId, testUserId)

        // Then
        assertEquals(2, result.size)
        verify(exactly = 1) { inferredEntityRepository.findByProposalId(testProposalId) }
    }

    @Test
    fun `getInferredEntitiesByProposal should filter by entity type when provided`() {
        // Given
        val entities = listOf(testEntity)
        val voteStats = VoteStatsResponse(upvotes = 0, downvotes = 0, score = 0, userVote = null)

        every {
            inferredEntityRepository.findByProposalIdAndEntityType(testProposalId, InferredEntityType.SUGGESTION)
        } returns entities
        every { voteService.getVoteStats(VoteTargetType.INFERRED_ENTITY, any(), testUserId) } returns voteStats
        every {
            commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(
                com.intrigsoft.ipitch.domain.CommentTargetType.INFERRED_ENTITY,
                any()
            )
        } returns 0L

        // When
        val result = inferredEntityService.getInferredEntitiesByProposal(
            testProposalId,
            testUserId,
            entityType = InferredEntityType.SUGGESTION
        )

        // Then
        assertEquals(1, result.size)
        assertEquals(InferredEntityType.SUGGESTION, result[0].entityType)
        verify(exactly = 1) { inferredEntityRepository.findByProposalIdAndEntityType(testProposalId, InferredEntityType.SUGGESTION) }
    }

    @Test
    fun `getInferredEntitiesByProposal should filter by status when provided`() {
        // Given
        val entities = listOf(testEntity)
        val voteStats = VoteStatsResponse(upvotes = 0, downvotes = 0, score = 0, userVote = null)

        every {
            inferredEntityRepository.findByProposalIdAndStatus(testProposalId, InferredEntityStatus.PENDING)
        } returns entities
        every { voteService.getVoteStats(VoteTargetType.INFERRED_ENTITY, any(), testUserId) } returns voteStats
        every {
            commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(
                com.intrigsoft.ipitch.domain.CommentTargetType.INFERRED_ENTITY,
                any()
            )
        } returns 0L

        // When
        val result = inferredEntityService.getInferredEntitiesByProposal(
            testProposalId,
            testUserId,
            status = InferredEntityStatus.PENDING
        )

        // Then
        assertEquals(1, result.size)
        assertEquals(InferredEntityStatus.PENDING, result[0].status)
        verify(exactly = 1) { inferredEntityRepository.findByProposalIdAndStatus(testProposalId, InferredEntityStatus.PENDING) }
    }

    @Test
    fun `getInferredEntitiesByProposal should filter by both type and status when provided`() {
        // Given
        val entities = listOf(testEntity)
        val voteStats = VoteStatsResponse(upvotes = 0, downvotes = 0, score = 0, userVote = null)

        every {
            inferredEntityRepository.findByProposalIdAndEntityTypeAndStatusOrderByConfidenceDesc(
                testProposalId,
                InferredEntityType.SUGGESTION,
                InferredEntityStatus.PENDING
            )
        } returns entities
        every { voteService.getVoteStats(VoteTargetType.INFERRED_ENTITY, any(), testUserId) } returns voteStats
        every {
            commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(
                com.intrigsoft.ipitch.domain.CommentTargetType.INFERRED_ENTITY,
                any()
            )
        } returns 0L

        // When
        val result = inferredEntityService.getInferredEntitiesByProposal(
            testProposalId,
            testUserId,
            entityType = InferredEntityType.SUGGESTION,
            status = InferredEntityStatus.PENDING
        )

        // Then
        assertEquals(1, result.size)
    }

    @Test
    fun `getInferredEntitiesByComment should return all entities extracted from comment`() {
        // Given
        val entities = listOf(testEntity, testEntity.copy(id = UUID.randomUUID()))
        val voteStats = VoteStatsResponse(upvotes = 0, downvotes = 0, score = 0, userVote = null)

        every { inferredEntityRepository.findBySourceCommentId(testCommentId) } returns entities
        every { voteService.getVoteStats(VoteTargetType.INFERRED_ENTITY, any(), testUserId) } returns voteStats
        every {
            commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(
                com.intrigsoft.ipitch.domain.CommentTargetType.INFERRED_ENTITY,
                any()
            )
        } returns 0L

        // When
        val result = inferredEntityService.getInferredEntitiesByComment(testCommentId, testUserId)

        // Then
        assertEquals(2, result.size)
    }

    @Test
    fun `updateStatus should update entity status and set reviewer info`() {
        // Given
        val entityId = testEntity.id!!
        val reviewerId = "reviewer-${UUID.randomUUID()}"
        val voteStats = VoteStatsResponse(upvotes = 0, downvotes = 0, score = 0, userVote = null)

        every { inferredEntityRepository.findById(entityId) } returns Optional.of(testEntity)
        every { inferredEntityRepository.save(any()) } answers {
            firstArg<InferredEntity>().apply {
                status = InferredEntityStatus.APPROVED
                reviewedBy = reviewerId
            }
        }
        every { voteService.getVoteStats(VoteTargetType.INFERRED_ENTITY, entityId, reviewerId) } returns voteStats
        every {
            commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(
                com.intrigsoft.ipitch.domain.CommentTargetType.INFERRED_ENTITY,
                entityId
            )
        } returns 0L
        every { elasticsearchSyncService.syncInferredEntity(any()) } just Runs

        // When
        val result = inferredEntityService.updateStatus(entityId, InferredEntityStatus.APPROVED, reviewerId)

        // Then
        assertNotNull(result)
        assertEquals(InferredEntityStatus.APPROVED, result.status)
        assertEquals(reviewerId, result.reviewedBy)
        assertNotNull(result.reviewedAt)
        verify(exactly = 1) { inferredEntityRepository.save(any()) }
        verify(exactly = 1) { elasticsearchSyncService.syncInferredEntity(any()) }
    }

    @Test
    fun `updateStatus should throw InferredEntityNotFoundException when entity does not exist`() {
        // Given
        val entityId = UUID.randomUUID()
        val reviewerId = "reviewer-${UUID.randomUUID()}"

        every { inferredEntityRepository.findById(entityId) } returns Optional.empty()

        // When & Then
        assertThrows<InferredEntityNotFoundException> {
            inferredEntityService.updateStatus(entityId, InferredEntityStatus.APPROVED, reviewerId)
        }
        verify(exactly = 0) { inferredEntityRepository.save(any()) }
    }

    @Test
    fun `updateContent should update entity content and summary`() {
        // Given
        val entityId = testEntity.id!!
        val newContent = "Updated content"
        val newSummary = "Updated summary"
        val voteStats = VoteStatsResponse(upvotes = 0, downvotes = 0, score = 0, userVote = null)

        every { inferredEntityRepository.findById(entityId) } returns Optional.of(testEntity)
        every { inferredEntityRepository.save(any()) } answers {
            firstArg<InferredEntity>().apply {
                content = newContent
                summary = newSummary
            }
        }
        every { voteService.getVoteStats(VoteTargetType.INFERRED_ENTITY, entityId, null) } returns voteStats
        every {
            commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(
                com.intrigsoft.ipitch.domain.CommentTargetType.INFERRED_ENTITY,
                entityId
            )
        } returns 0L
        every { elasticsearchSyncService.syncInferredEntity(any()) } just Runs

        // When
        val result = inferredEntityService.updateContent(entityId, newContent, newSummary)

        // Then
        assertNotNull(result)
        assertEquals(newContent, result.content)
        assertEquals(newSummary, result.summary)
        verify(exactly = 1) { inferredEntityRepository.save(any()) }
        verify(exactly = 1) { elasticsearchSyncService.syncInferredEntity(any()) }
    }

    @Test
    fun `updateContent should throw InferredEntityNotFoundException when entity does not exist`() {
        // Given
        val entityId = UUID.randomUUID()

        every { inferredEntityRepository.findById(entityId) } returns Optional.empty()

        // When & Then
        assertThrows<InferredEntityNotFoundException> {
            inferredEntityService.updateContent(entityId, "New content", "New summary")
        }
        verify(exactly = 0) { inferredEntityRepository.save(any()) }
    }
}
