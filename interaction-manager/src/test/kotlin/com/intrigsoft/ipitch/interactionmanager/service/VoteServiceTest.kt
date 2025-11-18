package com.intrigsoft.ipitch.interactionmanager.service

import com.intrigsoft.ipitch.domain.Vote
import com.intrigsoft.ipitch.domain.VoteTargetType
import com.intrigsoft.ipitch.domain.VoteType
import com.intrigsoft.ipitch.interactionmanager.dto.request.CreateVoteRequest
import com.intrigsoft.ipitch.interactionmanager.exception.UnauthorizedOperationException
import com.intrigsoft.ipitch.interactionmanager.exception.VoteNotFoundException
import com.intrigsoft.ipitch.repository.VoteRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class VoteServiceTest {

    @MockK
    private lateinit var voteRepository: VoteRepository

    @InjectMockKs
    private lateinit var voteService: VoteService

    private lateinit var testVote: Vote
    private lateinit var testUserId: String
    private lateinit var testTargetId: UUID

    @BeforeEach
    fun setUp() {
        testUserId = "user-${UUID.randomUUID()}"
        testTargetId = UUID.randomUUID()
        testVote = Vote(
            id = UUID.randomUUID(),
            userId = testUserId,
            targetType = VoteTargetType.PROPOSAL,
            targetId = testTargetId,
            voteType = VoteType.UP,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }

    @Test
    fun `createOrUpdateVote should create new vote when no existing vote`() {
        // Given
        val request = CreateVoteRequest(
            userId = testUserId,
            targetType = VoteTargetType.PROPOSAL,
            targetId = testTargetId,
            voteType = VoteType.UP
        )

        every { voteRepository.findByUserIdAndTargetTypeAndTargetId(testUserId, VoteTargetType.PROPOSAL, testTargetId) } returns null
        every { voteRepository.save(any()) } returns testVote

        // When
        val result = voteService.createOrUpdateVote(request)

        // Then
        assertNotNull(result)
        assertEquals(testVote.id, result.id)
        assertEquals(VoteType.UP, result.voteType)
        verify(exactly = 1) { voteRepository.save(any()) }
    }

    @Test
    fun `createOrUpdateVote should update existing vote when vote exists`() {
        // Given
        val existingVote = testVote.copy(voteType = VoteType.DOWN)
        val request = CreateVoteRequest(
            userId = testUserId,
            targetType = VoteTargetType.PROPOSAL,
            targetId = testTargetId,
            voteType = VoteType.UP
        )

        every { voteRepository.findByUserIdAndTargetTypeAndTargetId(testUserId, VoteTargetType.PROPOSAL, testTargetId) } returns existingVote
        every { voteRepository.save(any()) } returns existingVote.apply { voteType = VoteType.UP }

        // When
        val result = voteService.createOrUpdateVote(request)

        // Then
        assertNotNull(result)
        assertEquals(VoteType.UP, result.voteType)
        verify(exactly = 1) { voteRepository.save(any()) }
    }

    @Test
    fun `deleteVote should delete vote when user is authorized`() {
        // Given
        val voteId = testVote.id!!
        every { voteRepository.findById(voteId) } returns Optional.of(testVote)
        every { voteRepository.delete(testVote) } returns Unit

        // When
        val result = voteService.deleteVote(voteId, testUserId)

        // Then
        assertNotNull(result)
        assertEquals(voteId, result.id)
        verify(exactly = 1) { voteRepository.delete(testVote) }
    }

    @Test
    fun `deleteVote should throw UnauthorizedOperationException when user is not authorized`() {
        // Given
        val voteId = testVote.id!!
        val unauthorizedUserId = "unauthorized-user-${UUID.randomUUID()}"
        every { voteRepository.findById(voteId) } returns Optional.of(testVote)

        // When & Then
        assertThrows<UnauthorizedOperationException> {
            voteService.deleteVote(voteId, unauthorizedUserId)
        }
        verify(exactly = 0) { voteRepository.delete(any()) }
    }

    @Test
    fun `deleteVote should throw VoteNotFoundException when vote does not exist`() {
        // Given
        val voteId = UUID.randomUUID()
        every { voteRepository.findById(voteId) } returns Optional.empty()

        // When & Then
        assertThrows<VoteNotFoundException> {
            voteService.deleteVote(voteId, testUserId)
        }
    }

    @Test
    fun `getVote should return vote when it exists`() {
        // Given
        val voteId = testVote.id!!
        every { voteRepository.findById(voteId) } returns Optional.of(testVote)

        // When
        val result = voteService.getVote(voteId)

        // Then
        assertNotNull(result)
        assertEquals(voteId, result.id)
        assertEquals(testUserId, result.userId)
    }

    @Test
    fun `getVote should throw VoteNotFoundException when vote does not exist`() {
        // Given
        val voteId = UUID.randomUUID()
        every { voteRepository.findById(voteId) } returns Optional.empty()

        // When & Then
        assertThrows<VoteNotFoundException> {
            voteService.getVote(voteId)
        }
    }

    @Test
    fun `getUserVote should return vote when user has voted`() {
        // Given
        every { voteRepository.findByUserIdAndTargetTypeAndTargetId(testUserId, VoteTargetType.PROPOSAL, testTargetId) } returns testVote

        // When
        val result = voteService.getUserVote(testUserId, VoteTargetType.PROPOSAL, testTargetId)

        // Then
        assertNotNull(result)
        assertEquals(testVote.id, result?.id)
    }

    @Test
    fun `getUserVote should return null when user has not voted`() {
        // Given
        every { voteRepository.findByUserIdAndTargetTypeAndTargetId(testUserId, VoteTargetType.PROPOSAL, testTargetId) } returns null

        // When
        val result = voteService.getUserVote(testUserId, VoteTargetType.PROPOSAL, testTargetId)

        // Then
        assertNull(result)
    }

    @Test
    fun `getVoteStats should return correct statistics`() {
        // Given
        every { voteRepository.countUpvotes(VoteTargetType.PROPOSAL, testTargetId) } returns 10L
        every { voteRepository.countDownvotes(VoteTargetType.PROPOSAL, testTargetId) } returns 3L
        every { voteRepository.getVoteScore(VoteTargetType.PROPOSAL, testTargetId) } returns 7L
        every { voteRepository.findByUserIdAndTargetTypeAndTargetId(testUserId, VoteTargetType.PROPOSAL, testTargetId) } returns testVote

        // When
        val result = voteService.getVoteStats(VoteTargetType.PROPOSAL, testTargetId, testUserId)

        // Then
        assertEquals(10L, result.upvotes)
        assertEquals(3L, result.downvotes)
        assertEquals(7L, result.score)
        assertEquals("UP", result.userVote)
    }

    @Test
    fun `getVoteStats should return statistics without user vote when userId is null`() {
        // Given
        every { voteRepository.countUpvotes(VoteTargetType.PROPOSAL, testTargetId) } returns 5L
        every { voteRepository.countDownvotes(VoteTargetType.PROPOSAL, testTargetId) } returns 2L
        every { voteRepository.getVoteScore(VoteTargetType.PROPOSAL, testTargetId) } returns 3L

        // When
        val result = voteService.getVoteStats(VoteTargetType.PROPOSAL, testTargetId, null)

        // Then
        assertEquals(5L, result.upvotes)
        assertEquals(2L, result.downvotes)
        assertEquals(3L, result.score)
        assertNull(result.userVote)
    }

    @Test
    fun `getVotesByTarget should return all votes for a target`() {
        // Given
        val votes = listOf(testVote, testVote.copy(id = UUID.randomUUID(), voteType = VoteType.DOWN))
        every { voteRepository.findByTargetTypeAndTargetId(VoteTargetType.PROPOSAL, testTargetId) } returns votes

        // When
        val result = voteService.getVotesByTarget(VoteTargetType.PROPOSAL, testTargetId)

        // Then
        assertEquals(2, result.size)
    }

    @Test
    fun `removeVote should call repository delete method`() {
        // Given
        every { voteRepository.deleteByUserIdAndTargetTypeAndTargetId(testUserId, VoteTargetType.PROPOSAL, testTargetId) } returns Unit

        // When
        voteService.removeVote(testUserId, VoteTargetType.PROPOSAL, testTargetId)

        // Then
        verify(exactly = 1) { voteRepository.deleteByUserIdAndTargetTypeAndTargetId(testUserId, VoteTargetType.PROPOSAL, testTargetId) }
    }
}
