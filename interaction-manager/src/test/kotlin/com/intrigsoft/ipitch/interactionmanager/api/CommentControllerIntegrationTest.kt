package com.intrigsoft.ipitch.interactionmanager.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.intrigsoft.ipitch.domain.Comment
import com.intrigsoft.ipitch.domain.CommentTargetType
import com.intrigsoft.ipitch.interactionmanager.dto.request.CreateCommentRequest
import com.intrigsoft.ipitch.interactionmanager.dto.request.UpdateCommentRequest
import com.intrigsoft.ipitch.interactionmanager.search.CommentSearchRepository
import com.intrigsoft.ipitch.interactionmanager.search.InferredEntitySearchRepository
import com.intrigsoft.ipitch.interactionmanager.search.ProposalSearchRepository
import com.intrigsoft.ipitch.repository.CommentRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
    ]
)
class CommentControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var commentRepository: CommentRepository

    @MockBean
    private lateinit var commentSearchRepository: CommentSearchRepository

    @MockBean
    private lateinit var proposalSearchRepository: ProposalSearchRepository

    @MockBean
    private lateinit var inferredEntitySearchRepository: InferredEntitySearchRepository

    @MockBean
    private lateinit var commentAnalysisService: com.intrigsoft.ipitch.aiintegration.service.CommentAnalysisService

    private lateinit var testUserId: String
    private lateinit var testTargetId: UUID

    @BeforeEach
    fun setUp() {
        testUserId = "user-${UUID.randomUUID()}"
        testTargetId = UUID.randomUUID()
        commentRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        commentRepository.deleteAll()
    }

    @Test
    fun `createComment should create new comment successfully`() {
        // Given
        val request = CreateCommentRequest(
            userId = testUserId,
            content = "Test comment content",
            parentCommentId = null,
            targetType = CommentTargetType.PROPOSAL,
            targetId = testTargetId
        )

        // When & Then
        mockMvc.perform(
            post("/api/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userId").value(testUserId.toString()))
            .andExpect(jsonPath("$.data.content").value("Test comment content"))
            .andExpect(jsonPath("$.data.targetType").value("PROPOSAL"))
            .andExpect(jsonPath("$.data.deleted").value(false))
    }

    @Test
    fun `createComment should create reply to existing comment`() {
        // Given - create parent comment
        val parentComment = Comment(
            userId = testUserId,
            content = "Parent comment",
            parentComment = null,
            targetType = CommentTargetType.PROPOSAL,
            targetId = testTargetId
        )
        val savedParent = commentRepository.save(parentComment)

        val replyRequest = CreateCommentRequest(
            userId = testUserId,
            content = "Reply comment",
            parentCommentId = savedParent.id,
            targetType = CommentTargetType.COMMENT,
            targetId = testTargetId
        )

        // When & Then
        mockMvc.perform(
            post("/api/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(replyRequest))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").value("Reply comment"))
            .andExpect(jsonPath("$.data.parentCommentId").value(savedParent.id.toString()))
    }

    @Test
    fun `createComment should validate content is not blank`() {
        // Given - request with blank content
        val invalidRequest = CreateCommentRequest(
            userId = testUserId,
            content = "",
            parentCommentId = null,
            targetType = CommentTargetType.PROPOSAL,
            targetId = testTargetId
        )

        // When & Then
        mockMvc.perform(
            post("/api/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `updateComment should update content when user is authorized`() {
        // Given
        val comment = Comment(
            userId = testUserId,
            content = "Original content",
            parentComment = null,
            targetType = CommentTargetType.PROPOSAL,
            targetId = testTargetId
        )
        val savedComment = commentRepository.save(comment)

        val updateRequest = UpdateCommentRequest(content = "Updated content")

        // When & Then
        mockMvc.perform(
            put("/api/comments/${savedComment.id}")
                .param("userId", testUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").value("Updated content"))
    }

    @Test
    fun `updateComment should return 403 when user is not authorized`() {
        // Given
        val comment = Comment(
            userId = testUserId,
            content = "Original content",
            parentComment = null,
            targetType = CommentTargetType.PROPOSAL,
            targetId = testTargetId
        )
        val savedComment = commentRepository.save(comment)
        val unauthorizedUserId = UUID.randomUUID()

        val updateRequest = UpdateCommentRequest(content = "Updated content")

        // When & Then
        mockMvc.perform(
            put("/api/comments/${savedComment.id}")
                .param("userId", unauthorizedUserId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `deleteComment should soft delete comment when user is authorized`() {
        // Given
        val comment = Comment(
            userId = testUserId,
            content = "Test comment",
            parentComment = null,
            targetType = CommentTargetType.PROPOSAL,
            targetId = testTargetId
        )
        val savedComment = commentRepository.save(comment)

        // When & Then
        mockMvc.perform(
            delete("/api/comments/${savedComment.id}")
                .param("userId", testUserId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.deleted").value(true))
    }

    @Test
    fun `getComment should return comment when it exists`() {
        // Given
        val comment = Comment(
            userId = testUserId,
            content = "Test comment",
            parentComment = null,
            targetType = CommentTargetType.PROPOSAL,
            targetId = testTargetId
        )
        val savedComment = commentRepository.save(comment)

        // When & Then
        mockMvc.perform(get("/api/comments/${savedComment.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(savedComment.id.toString()))
            .andExpect(jsonPath("$.data.content").value("Test comment"))
    }

    @Test
    fun `getComment should return 404 when comment does not exist`() {
        // Given
        val nonExistentId = UUID.randomUUID()

        // When & Then
        mockMvc.perform(get("/api/comments/$nonExistentId"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `getCommentsByTarget should return all top-level comments for target`() {
        // Given - create multiple comments
        commentRepository.save(
            Comment(
                userId = testUserId,
                content = "Comment 1",
                parentComment = null,
                targetType = CommentTargetType.PROPOSAL,
                targetId = testTargetId
            )
        )
        commentRepository.save(
            Comment(
                userId = testUserId,
                content = "Comment 2",
                parentComment = null,
                targetType = CommentTargetType.PROPOSAL,
                targetId = testTargetId
            )
        )

        // When & Then
        mockMvc.perform(
            get("/api/comments")
                .param("targetType", "PROPOSAL")
                .param("targetId", testTargetId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(2))
    }

    @Test
    fun `getReplies should return all replies to a comment`() {
        // Given - create parent and replies
        val parentComment = Comment(
            userId = testUserId,
            content = "Parent comment",
            parentComment = null,
            targetType = CommentTargetType.PROPOSAL,
            targetId = testTargetId
        )
        val savedParent = commentRepository.save(parentComment)

        commentRepository.save(
            Comment(
                userId = testUserId,
                content = "Reply 1",
                parentComment = savedParent,
                targetType = CommentTargetType.COMMENT,
                targetId = testTargetId
            )
        )
        commentRepository.save(
            Comment(
                userId = testUserId,
                content = "Reply 2",
                parentComment = savedParent,
                targetType = CommentTargetType.COMMENT,
                targetId = testTargetId
            )
        )

        // When & Then
        mockMvc.perform(get("/api/comments/${savedParent.id}/replies"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(2))
    }

    @Test
    fun `getUserComments should return all comments by user`() {
        // Given
        commentRepository.save(
            Comment(
                userId = testUserId,
                content = "User comment 1",
                parentComment = null,
                targetType = CommentTargetType.PROPOSAL,
                targetId = testTargetId
            )
        )
        commentRepository.save(
            Comment(
                userId = testUserId,
                content = "User comment 2",
                parentComment = null,
                targetType = CommentTargetType.PROPOSAL,
                targetId = UUID.randomUUID()
            )
        )

        // When & Then
        mockMvc.perform(get("/api/comments/user/$testUserId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(2))
    }
}
