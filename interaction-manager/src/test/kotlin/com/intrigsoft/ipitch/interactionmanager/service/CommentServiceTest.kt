package com.intrigsoft.ipitch.interactionmanager.service

import com.intrigsoft.ipitch.domain.Comment
import com.intrigsoft.ipitch.domain.CommentTargetType
import com.intrigsoft.ipitch.domain.VoteTargetType
import com.intrigsoft.ipitch.interactionmanager.dto.request.CreateCommentRequest
import com.intrigsoft.ipitch.interactionmanager.dto.request.UpdateCommentRequest
import com.intrigsoft.ipitch.interactionmanager.dto.response.VoteStatsResponse
import com.intrigsoft.ipitch.interactionmanager.exception.CommentNotFoundException
import com.intrigsoft.ipitch.interactionmanager.exception.InvalidOperationException
import com.intrigsoft.ipitch.interactionmanager.exception.UnauthorizedOperationException
import com.intrigsoft.ipitch.repository.CommentRepository
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
class CommentServiceTest {

    @MockK
    private lateinit var commentRepository: CommentRepository

    @MockK
    private lateinit var voteService: VoteService

    @MockK
    private lateinit var elasticsearchSyncService: ElasticsearchSyncService

    @MockK
    private lateinit var proposalRepository: com.intrigsoft.ipitch.repository.ProposalRepository

    @InjectMockKs
    private lateinit var commentService: CommentService

    private lateinit var testComment: Comment
    private lateinit var testUserId: String
    private lateinit var testTargetId: UUID

    @BeforeEach
    fun setUp() {
        testUserId = "user-${UUID.randomUUID()}"
        testTargetId = UUID.randomUUID()
        testComment = Comment(
            id = UUID.randomUUID(),
            userId = testUserId,
            content = "Test comment",
            parentComment = null,
            targetType = CommentTargetType.PROPOSAL,
            targetId = testTargetId,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            deleted = false
        )
    }

    @Test
    fun `createComment should create new comment successfully`() {
        // Given
        val request = CreateCommentRequest(
            userId = testUserId,
            content = "Test comment",
            parentCommentId = null,
            targetType = CommentTargetType.PROPOSAL,
            targetId = testTargetId
        )

        val voteStats = VoteStatsResponse(upvotes = 0, downvotes = 0, score = 0, userVote = null)

        every { commentRepository.save(any()) } returns testComment
        every { voteService.getVoteStats(VoteTargetType.COMMENT, testComment.id!!, testUserId) } returns voteStats
        every { commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(CommentTargetType.COMMENT, testComment.id!!) } returns 0L
        every { elasticsearchSyncService.syncComment(any()) } just Runs

        // When
        val result = commentService.createComment(request)

        // Then
        assertNotNull(result)
        assertEquals(testComment.id, result.id)
        assertEquals("Test comment", result.content)
        assertFalse(result.deleted)
        verify(exactly = 1) { commentRepository.save(any()) }
        verify(exactly = 1) { elasticsearchSyncService.syncComment(any()) }
    }

    @Test
    fun `createComment should create reply to existing comment`() {
        // Given
        val parentComment = testComment.copy()
        val request = CreateCommentRequest(
            userId = testUserId,
            content = "Reply comment",
            parentCommentId = parentComment.id,
            targetType = CommentTargetType.COMMENT,
            targetId = testTargetId
        )

        val replyComment = testComment.copy(
            id = UUID.randomUUID(),
            content = "Reply comment",
            parentComment = parentComment
        )

        val voteStats = VoteStatsResponse(upvotes = 0, downvotes = 0, score = 0, userVote = null)

        every { commentRepository.findById(parentComment.id!!) } returns Optional.of(parentComment)
        every { commentRepository.save(any()) } returns replyComment
        every { voteService.getVoteStats(VoteTargetType.COMMENT, replyComment.id!!, testUserId) } returns voteStats
        every { commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(CommentTargetType.COMMENT, replyComment.id!!) } returns 0L
        every { elasticsearchSyncService.syncComment(any()) } just Runs

        // When
        val result = commentService.createComment(request)

        // Then
        assertNotNull(result)
        assertEquals(parentComment.id, result.parentCommentId)
        verify(exactly = 1) { commentRepository.save(any()) }
    }

    @Test
    fun `createComment should throw InvalidOperationException when parent comment not found`() {
        // Given
        val nonExistentParentId = UUID.randomUUID()
        val request = CreateCommentRequest(
            userId = testUserId,
            content = "Reply comment",
            parentCommentId = nonExistentParentId,
            targetType = CommentTargetType.COMMENT,
            targetId = testTargetId
        )

        every { commentRepository.findById(nonExistentParentId) } returns Optional.empty()

        // When & Then
        assertThrows<InvalidOperationException> {
            commentService.createComment(request)
        }
        verify(exactly = 0) { commentRepository.save(any()) }
    }

    @Test
    fun `createComment should throw InvalidOperationException when parent comment is deleted`() {
        // Given
        val deletedParent = testComment.copy(deleted = true)
        val request = CreateCommentRequest(
            userId = testUserId,
            content = "Reply comment",
            parentCommentId = deletedParent.id,
            targetType = CommentTargetType.COMMENT,
            targetId = testTargetId
        )

        every { commentRepository.findById(deletedParent.id!!) } returns Optional.of(deletedParent)

        // When & Then
        assertThrows<InvalidOperationException> {
            commentService.createComment(request)
        }
        verify(exactly = 0) { commentRepository.save(any()) }
    }

    @Test
    fun `updateComment should update comment successfully when user is authorized`() {
        // Given
        val commentId = testComment.id!!
        val request = UpdateCommentRequest(content = "Updated content")
        val updatedComment = testComment.copy(content = "Updated content")
        val voteStats = VoteStatsResponse(upvotes = 0, downvotes = 0, score = 0, userVote = null)

        every { commentRepository.findById(commentId) } returns Optional.of(testComment)
        every { commentRepository.save(any()) } returns updatedComment
        every { voteService.getVoteStats(VoteTargetType.COMMENT, commentId, testUserId) } returns voteStats
        every { commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(CommentTargetType.COMMENT, commentId) } returns 0L
        every { elasticsearchSyncService.syncComment(any()) } just Runs

        // When
        val result = commentService.updateComment(commentId, request, testUserId)

        // Then
        assertNotNull(result)
        assertEquals("Updated content", result.content)
        verify(exactly = 1) { commentRepository.save(any()) }
        verify(exactly = 1) { elasticsearchSyncService.syncComment(any()) }
    }

    @Test
    fun `updateComment should throw UnauthorizedOperationException when user is not owner`() {
        // Given
        val commentId = testComment.id!!
        val request = UpdateCommentRequest(content = "Updated content")
        val unauthorizedUserId = "unauthorized-user-${UUID.randomUUID()}"

        every { commentRepository.findById(commentId) } returns Optional.of(testComment)

        // When & Then
        assertThrows<UnauthorizedOperationException> {
            commentService.updateComment(commentId, request, unauthorizedUserId)
        }
        verify(exactly = 0) { commentRepository.save(any()) }
    }

    @Test
    fun `updateComment should throw CommentNotFoundException when comment does not exist`() {
        // Given
        val commentId = UUID.randomUUID()
        val request = UpdateCommentRequest(content = "Updated content")

        every { commentRepository.findById(commentId) } returns Optional.empty()

        // When & Then
        assertThrows<CommentNotFoundException> {
            commentService.updateComment(commentId, request, testUserId)
        }
    }

    @Test
    fun `updateComment should throw InvalidOperationException when comment is deleted`() {
        // Given
        val deletedComment = testComment.copy(deleted = true)
        val request = UpdateCommentRequest(content = "Updated content")

        every { commentRepository.findById(deletedComment.id!!) } returns Optional.of(deletedComment)

        // When & Then
        assertThrows<InvalidOperationException> {
            commentService.updateComment(deletedComment.id!!, request, testUserId)
        }
        verify(exactly = 0) { commentRepository.save(any()) }
    }

    @Test
    fun `deleteComment should soft delete comment when user is authorized`() {
        // Given
        val commentId = testComment.id!!
        val voteStats = VoteStatsResponse(upvotes = 0, downvotes = 0, score = 0, userVote = null)

        every { commentRepository.findById(commentId) } returns Optional.of(testComment)
        every { commentRepository.save(any()) } answers {
            firstArg<Comment>().apply { deleted = true }
        }
        every { voteService.getVoteStats(VoteTargetType.COMMENT, commentId, testUserId) } returns voteStats
        every { commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(CommentTargetType.COMMENT, commentId) } returns 0L
        every { elasticsearchSyncService.syncComment(any()) } just Runs

        // When
        val result = commentService.deleteComment(commentId, testUserId)

        // Then
        assertNotNull(result)
        assertTrue(result.deleted)
        verify(exactly = 1) { commentRepository.save(any()) }
        verify(exactly = 1) { elasticsearchSyncService.syncComment(any()) }
    }

    @Test
    fun `deleteComment should throw UnauthorizedOperationException when user is not owner`() {
        // Given
        val commentId = testComment.id!!
        val unauthorizedUserId = "unauthorized-user-${UUID.randomUUID()}"

        every { commentRepository.findById(commentId) } returns Optional.of(testComment)

        // When & Then
        assertThrows<UnauthorizedOperationException> {
            commentService.deleteComment(commentId, unauthorizedUserId)
        }
        verify(exactly = 0) { commentRepository.save(any()) }
    }

    @Test
    fun `getComment should return comment when it exists`() {
        // Given
        val commentId = testComment.id!!
        val voteStats = VoteStatsResponse(upvotes = 5, downvotes = 2, score = 3, userVote = "UP")

        every { commentRepository.findById(commentId) } returns Optional.of(testComment)
        every { voteService.getVoteStats(VoteTargetType.COMMENT, commentId, testUserId) } returns voteStats
        every { commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(CommentTargetType.COMMENT, commentId) } returns 3L

        // When
        val result = commentService.getComment(commentId, testUserId)

        // Then
        assertNotNull(result)
        assertEquals(commentId, result.id)
        assertEquals(3L, result.voteStats.score)
        assertEquals(3L, result.replyCount)
    }

    @Test
    fun `getComment should throw CommentNotFoundException when comment does not exist`() {
        // Given
        val commentId = UUID.randomUUID()

        every { commentRepository.findById(commentId) } returns Optional.empty()

        // When & Then
        assertThrows<CommentNotFoundException> {
            commentService.getComment(commentId, testUserId)
        }
    }

    @Test
    fun `getCommentsByTarget should return all non-deleted top-level comments for target`() {
        // Given
        val comments = listOf(testComment, testComment.copy(id = UUID.randomUUID()))
        val voteStats = VoteStatsResponse(upvotes = 0, downvotes = 0, score = 0, userVote = null)

        every {
            commentRepository.findByTargetTypeAndTargetIdAndParentCommentIsNullAndDeletedFalse(
                CommentTargetType.PROPOSAL,
                testTargetId
            )
        } returns comments

        every { voteService.getVoteStats(VoteTargetType.COMMENT, any(), testUserId) } returns voteStats
        every { commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(CommentTargetType.COMMENT, any()) } returns 0L

        // When
        val result = commentService.getCommentsByTarget(CommentTargetType.PROPOSAL, testTargetId, testUserId)

        // Then
        assertEquals(2, result.size)
    }

    @Test
    fun `getReplies should return all non-deleted replies to a comment`() {
        // Given
        val parentCommentId = testComment.id!!
        val reply1 = testComment.copy(id = UUID.randomUUID(), parentComment = testComment)
        val reply2 = testComment.copy(id = UUID.randomUUID(), parentComment = testComment)
        val replies = listOf(reply1, reply2)
        val voteStats = VoteStatsResponse(upvotes = 0, downvotes = 0, score = 0, userVote = null)

        every { commentRepository.findById(parentCommentId) } returns Optional.of(testComment)
        every { commentRepository.findByParentCommentAndDeletedFalse(testComment) } returns replies
        every { voteService.getVoteStats(VoteTargetType.COMMENT, any(), testUserId) } returns voteStats
        every { commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(CommentTargetType.COMMENT, any()) } returns 0L

        // When
        val result = commentService.getReplies(parentCommentId, testUserId)

        // Then
        assertEquals(2, result.size)
    }

    @Test
    fun `getUserComments should return all non-deleted comments by user`() {
        // Given
        val comments = listOf(testComment, testComment.copy(id = UUID.randomUUID()))
        val voteStats = VoteStatsResponse(upvotes = 0, downvotes = 0, score = 0, userVote = null)

        every { commentRepository.findByUserIdAndDeletedFalse(testUserId) } returns comments
        every { voteService.getVoteStats(VoteTargetType.COMMENT, any(), testUserId) } returns voteStats
        every { commentRepository.countByTargetTypeAndTargetIdAndDeletedFalse(CommentTargetType.COMMENT, any()) } returns 0L

        // When
        val result = commentService.getUserComments(testUserId)

        // Then
        assertEquals(2, result.size)
    }
}
