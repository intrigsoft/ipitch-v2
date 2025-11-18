package com.intrigsoft.ipitch.interactionmanager.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.intrigsoft.ipitch.domain.InferredEntity
import com.intrigsoft.ipitch.domain.InferredEntityStatus
import com.intrigsoft.ipitch.domain.InferredEntityType
import com.intrigsoft.ipitch.interactionmanager.dto.request.UpdateInferredEntityContentRequest
import com.intrigsoft.ipitch.interactionmanager.dto.request.UpdateInferredEntityStatusRequest
import com.intrigsoft.ipitch.interactionmanager.search.CommentSearchRepository
import com.intrigsoft.ipitch.interactionmanager.search.InferredEntitySearchRepository
import com.intrigsoft.ipitch.interactionmanager.search.ProposalSearchRepository
import com.intrigsoft.ipitch.repository.InferredEntityRepository
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InferredEntityControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var inferredEntityRepository: InferredEntityRepository

    @MockBean
    private lateinit var commentSearchRepository: CommentSearchRepository

    @MockBean
    private lateinit var proposalSearchRepository: ProposalSearchRepository

    @MockBean
    private lateinit var inferredEntitySearchRepository: InferredEntitySearchRepository

    @MockBean
    private lateinit var commentAnalysisService: com.intrigsoft.ipitch.aiintegration.service.CommentAnalysisService

    private lateinit var testProposalId: UUID
    private lateinit var testCommentId: UUID

    @BeforeEach
    fun setUp() {
        testProposalId = UUID.randomUUID()
        testCommentId = UUID.randomUUID()
        inferredEntityRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        inferredEntityRepository.deleteAll()
    }

    @Test
    fun `getInferredEntity should return entity when it exists`() {
        // Given
        val entity = InferredEntity(
            proposalId = testProposalId,
            sourceCommentId = testCommentId,
            entityType = InferredEntityType.SUGGESTION,
            content = "Test suggestion",
            summary = "Test summary",
            status = InferredEntityStatus.PENDING,
            confidenceScore = 0.85,
            metadata = mapOf("keywords" to listOf("test"))
        )
        val savedEntity = inferredEntityRepository.save(entity)

        // When & Then
        mockMvc.perform(get("/api/inferred-entities/${savedEntity.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(savedEntity.id.toString()))
            .andExpect(jsonPath("$.data.entityType").value("SUGGESTION"))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.confidenceScore").value(0.85))
    }

    @Test
    fun `getInferredEntity should return 404 when entity does not exist`() {
        // Given
        val nonExistentId = UUID.randomUUID()

        // When & Then
        mockMvc.perform(get("/api/inferred-entities/$nonExistentId"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `getInferredEntitiesByProposal should return all entities for proposal`() {
        // Given
        inferredEntityRepository.save(
            InferredEntity(
                proposalId = testProposalId,
                sourceCommentId = testCommentId,
                entityType = InferredEntityType.SUGGESTION,
                content = "Suggestion 1",
                summary = "Summary 1",
                status = InferredEntityStatus.PENDING,
                confidenceScore = 0.9
            )
        )
        inferredEntityRepository.save(
            InferredEntity(
                proposalId = testProposalId,
                sourceCommentId = testCommentId,
                entityType = InferredEntityType.CONCERN,
                content = "Concern 1",
                summary = "Summary 2",
                status = InferredEntityStatus.PENDING,
                confidenceScore = 0.8
            )
        )

        // When & Then
        mockMvc.perform(get("/api/inferred-entities/proposal/$testProposalId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(2))
    }

    @Test
    fun `getInferredEntitiesByProposal should filter by entity type`() {
        // Given
        inferredEntityRepository.save(
            InferredEntity(
                proposalId = testProposalId,
                sourceCommentId = testCommentId,
                entityType = InferredEntityType.SUGGESTION,
                content = "Suggestion 1",
                summary = "Summary 1",
                status = InferredEntityStatus.PENDING,
                confidenceScore = 0.9
            )
        )
        inferredEntityRepository.save(
            InferredEntity(
                proposalId = testProposalId,
                sourceCommentId = testCommentId,
                entityType = InferredEntityType.CONCERN,
                content = "Concern 1",
                summary = "Summary 2",
                status = InferredEntityStatus.PENDING,
                confidenceScore = 0.8
            )
        )

        // When & Then
        mockMvc.perform(
            get("/api/inferred-entities/proposal/$testProposalId")
                .param("entityType", "SUGGESTION")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].entityType").value("SUGGESTION"))
    }

    @Test
    fun `getInferredEntitiesByProposal should filter by status`() {
        // Given
        inferredEntityRepository.save(
            InferredEntity(
                proposalId = testProposalId,
                sourceCommentId = testCommentId,
                entityType = InferredEntityType.SUGGESTION,
                content = "Suggestion 1",
                summary = "Summary 1",
                status = InferredEntityStatus.PENDING,
                confidenceScore = 0.9
            )
        )
        inferredEntityRepository.save(
            InferredEntity(
                proposalId = testProposalId,
                sourceCommentId = testCommentId,
                entityType = InferredEntityType.SUGGESTION,
                content = "Suggestion 2",
                summary = "Summary 2",
                status = InferredEntityStatus.APPROVED,
                confidenceScore = 0.8
            )
        )

        // When & Then
        mockMvc.perform(
            get("/api/inferred-entities/proposal/$testProposalId")
                .param("status", "APPROVED")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].status").value("APPROVED"))
    }

    @Test
    fun `getInferredEntitiesByComment should return entities from comment`() {
        // Given
        inferredEntityRepository.save(
            InferredEntity(
                proposalId = testProposalId,
                sourceCommentId = testCommentId,
                entityType = InferredEntityType.SUGGESTION,
                content = "Suggestion from comment",
                summary = "Summary",
                status = InferredEntityStatus.PENDING,
                confidenceScore = 0.9
            )
        )

        // When & Then
        mockMvc.perform(get("/api/inferred-entities/comment/$testCommentId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].sourceCommentId").value(testCommentId.toString()))
    }

    @Test
    fun `updateStatus should update entity status and reviewer info`() {
        // Given
        val entity = InferredEntity(
            proposalId = testProposalId,
            sourceCommentId = testCommentId,
            entityType = InferredEntityType.SUGGESTION,
            content = "Test suggestion",
            summary = "Test summary",
            status = InferredEntityStatus.PENDING,
            confidenceScore = 0.85
        )
        val savedEntity = inferredEntityRepository.save(entity)

        val reviewerId = "reviewer-${UUID.randomUUID()}"
        val request = UpdateInferredEntityStatusRequest(
            status = InferredEntityStatus.APPROVED,
            reviewerId = reviewerId
        )

        // When & Then
        mockMvc.perform(
            put("/api/inferred-entities/${savedEntity.id}/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.reviewedBy").value(reviewerId.toString()))
            .andExpect(jsonPath("$.data.reviewedAt").exists())
    }

    @Test
    fun `updateStatus should validate required fields`() {
        // Given
        val entity = InferredEntity(
            proposalId = testProposalId,
            sourceCommentId = testCommentId,
            entityType = InferredEntityType.SUGGESTION,
            content = "Test suggestion",
            summary = "Test summary",
            status = InferredEntityStatus.PENDING,
            confidenceScore = 0.85
        )
        val savedEntity = inferredEntityRepository.save(entity)

        val invalidRequest = mapOf("status" to "APPROVED")  // Missing reviewerId

        // When & Then
        mockMvc.perform(
            put("/api/inferred-entities/${savedEntity.id}/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `updateContent should update entity content and summary`() {
        // Given
        val entity = InferredEntity(
            proposalId = testProposalId,
            sourceCommentId = testCommentId,
            entityType = InferredEntityType.SUGGESTION,
            content = "Original content",
            summary = "Original summary",
            status = InferredEntityStatus.PENDING,
            confidenceScore = 0.85
        )
        val savedEntity = inferredEntityRepository.save(entity)

        val request = UpdateInferredEntityContentRequest(
            content = "Updated content",
            summary = "Updated summary"
        )

        // When & Then
        mockMvc.perform(
            put("/api/inferred-entities/${savedEntity.id}/content")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").value("Updated content"))
            .andExpect(jsonPath("$.data.summary").value("Updated summary"))
    }

    @Test
    fun `updateContent should validate content is not blank`() {
        // Given
        val entity = InferredEntity(
            proposalId = testProposalId,
            sourceCommentId = testCommentId,
            entityType = InferredEntityType.SUGGESTION,
            content = "Original content",
            summary = "Original summary",
            status = InferredEntityStatus.PENDING,
            confidenceScore = 0.85
        )
        val savedEntity = inferredEntityRepository.save(entity)

        val invalidRequest = UpdateInferredEntityContentRequest(
            content = "",  // Blank content
            summary = "Updated summary"
        )

        // When & Then
        mockMvc.perform(
            put("/api/inferred-entities/${savedEntity.id}/content")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
    }
}
