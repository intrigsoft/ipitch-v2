package com.intrigsoft.ipitch.proposalviewmanager.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.intrigsoft.ipitch.domain.ContributorStatus
import com.intrigsoft.ipitch.domain.ProposalStatus
import com.intrigsoft.ipitch.proposalviewmanager.dto.ProposalPublishDto
import com.intrigsoft.ipitch.proposalviewmanager.service.ProposalIndexService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDateTime
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalProposalControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var proposalIndexService: ProposalIndexService

    @Test
    fun `publishProposal should index proposal successfully`() {
        // Given
        val proposalId = UUID.randomUUID()
        val publishDto = ProposalPublishDto(
            id = proposalId,
            title = "Test Proposal",
            content = "Test content",
            ownerId = UUID.randomUUID(),
            ownerName = "Test Owner",
            contributors = listOf(
                ProposalPublishDto.ContributorDto(
                    id = UUID.randomUUID(),
                    userId = UUID.randomUUID(),
                    userName = "Contributor",
                    role = "reviewer",
                    status = ContributorStatus.ACTIVE.name
                )
            ),
            version = "1.0.0",
            status = ProposalStatus.PUBLISHED,
            stats = mapOf("views" to 100),
            workingBranch = "proposal/test",
            gitCommitHash = "abc123",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { proposalIndexService.indexProposal(any()) } just runs

        // When & Then
        mockMvc.perform(
            post("/internal/api/v1/proposals/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(publishDto))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Proposal published successfully"))
            .andExpect(jsonPath("$.data").value(proposalId.toString()))

        verify(exactly = 1) { proposalIndexService.indexProposal(any()) }
    }

    @Test
    fun `publishProposal should handle indexing errors`() {
        // Given
        val proposalId = UUID.randomUUID()
        val publishDto = ProposalPublishDto(
            id = proposalId,
            title = "Test Proposal",
            content = "Test content",
            ownerId = UUID.randomUUID(),
            ownerName = "Test Owner",
            contributors = emptyList(),
            version = "1.0.0",
            status = ProposalStatus.PUBLISHED,
            stats = emptyMap(),
            workingBranch = "proposal/test",
            gitCommitHash = "abc123",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { proposalIndexService.indexProposal(any()) } throws RuntimeException("Elasticsearch error")

        // When & Then
        mockMvc.perform(
            post("/internal/api/v1/proposals/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(publishDto))
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `deleteProposal should delete proposal from index successfully`() {
        // Given
        val proposalId = UUID.randomUUID()

        every { proposalIndexService.deleteProposal(proposalId.toString()) } just runs

        // When & Then
        mockMvc.perform(
            delete("/internal/api/v1/proposals/$proposalId")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Proposal deleted successfully"))

        verify(exactly = 1) { proposalIndexService.deleteProposal(proposalId.toString()) }
    }

    @Test
    fun `deleteProposal should handle deletion errors`() {
        // Given
        val proposalId = UUID.randomUUID()

        every { proposalIndexService.deleteProposal(proposalId.toString()) } throws RuntimeException("Delete failed")

        // When & Then
        mockMvc.perform(
            delete("/internal/api/v1/proposals/$proposalId")
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `publishProposal should accept proposal with minimal fields`() {
        // Given
        val proposalId = UUID.randomUUID()
        val publishDto = ProposalPublishDto(
            id = proposalId,
            title = "Minimal Proposal",
            content = "Content",
            ownerId = UUID.randomUUID(),
            ownerName = null,
            contributors = emptyList(),
            version = "1.0.0",
            status = ProposalStatus.DRAFT,
            stats = emptyMap(),
            workingBranch = null,
            gitCommitHash = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { proposalIndexService.indexProposal(any()) } just runs

        // When & Then
        mockMvc.perform(
            post("/internal/api/v1/proposals/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(publishDto))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        verify(exactly = 1) { proposalIndexService.indexProposal(any()) }
    }
}
