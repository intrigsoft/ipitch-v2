package com.intrigsoft.ipitch.proposalviewmanager.api

import com.intrigsoft.ipitch.domain.ProposalStatus
import com.intrigsoft.ipitch.proposalviewmanager.dto.ContributorDto
import com.intrigsoft.ipitch.proposalviewmanager.dto.PagedProposalSearchResponse
import com.intrigsoft.ipitch.proposalviewmanager.dto.ProposalSearchResponse
import com.intrigsoft.ipitch.proposalviewmanager.service.ProposalSearchService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDateTime
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProposalViewControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var proposalSearchService: ProposalSearchService

    @Test
    fun `searchProposals should return results with default parameters`() {
        // Given
        val proposalId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        val searchResponse = ProposalSearchResponse(
            id = proposalId,
            title = "Test Proposal",
            content = "Test content",
            ownerId = ownerId,
            ownerName = "Test Owner",
            contributors = emptyList(),
            version = "1.0.0",
            status = ProposalStatus.PUBLISHED,
            stats = mapOf("views" to 100),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val pagedResponse = PagedProposalSearchResponse(
            content = listOf(searchResponse),
            totalElements = 1,
            totalPages = 1,
            page = 0,
            size = 20
        )

        every { proposalSearchService.searchProposals(any()) } returns pagedResponse

        // When & Then
        mockMvc.perform(get("/api/v1/proposals/search"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(proposalId.toString()))
            .andExpect(jsonPath("$.content[0].title").value("Test Proposal"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
    }

    @Test
    fun `searchProposals should filter by query parameter`() {
        // Given
        val proposalId = UUID.randomUUID()
        val searchResponse = ProposalSearchResponse(
            id = proposalId,
            title = "Matching Proposal",
            content = "Content",
            ownerId = UUID.randomUUID(),
            ownerName = "Owner",
            contributors = emptyList(),
            version = "1.0.0",
            status = ProposalStatus.PUBLISHED,
            stats = emptyMap(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val pagedResponse = PagedProposalSearchResponse(
            content = listOf(searchResponse),
            totalElements = 1,
            totalPages = 1,
            page = 0,
            size = 20
        )

        every { proposalSearchService.searchProposals(any()) } returns pagedResponse

        // When & Then
        mockMvc.perform(get("/api/v1/proposals/search").param("query", "Matching"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].title").value("Matching Proposal"))
    }

    @Test
    fun `searchProposals should filter by ownerId and status`() {
        // Given
        val ownerId = UUID.randomUUID()
        val proposalId = UUID.randomUUID()
        val searchResponse = ProposalSearchResponse(
            id = proposalId,
            title = "Published Proposal",
            content = "Content",
            ownerId = ownerId,
            ownerName = "Owner",
            contributors = emptyList(),
            version = "1.0.0",
            status = ProposalStatus.PUBLISHED,
            stats = emptyMap(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val pagedResponse = PagedProposalSearchResponse(
            content = listOf(searchResponse),
            totalElements = 1,
            totalPages = 1,
            page = 0,
            size = 20
        )

        every { proposalSearchService.searchProposals(any()) } returns pagedResponse

        // When & Then
        mockMvc.perform(
            get("/api/v1/proposals/search")
                .param("ownerId", ownerId.toString())
                .param("status", "PUBLISHED")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].ownerId").value(ownerId.toString()))
            .andExpect(jsonPath("$.content[0].status").value("PUBLISHED"))
    }

    @Test
    fun `searchProposals should handle pagination parameters`() {
        // Given
        val pagedResponse = PagedProposalSearchResponse(
            content = emptyList(),
            totalElements = 100,
            totalPages = 10,
            page = 2,
            size = 10
        )

        every { proposalSearchService.searchProposals(any()) } returns pagedResponse

        // When & Then
        mockMvc.perform(
            get("/api/v1/proposals/search")
                .param("page", "2")
                .param("size", "10")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(100))
            .andExpect(jsonPath("$.totalPages").value(10))
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.size").value(10))
    }

    @Test
    fun `searchProposals should handle sort parameters`() {
        // Given
        val proposalId = UUID.randomUUID()
        val searchResponse = ProposalSearchResponse(
            id = proposalId,
            title = "Proposal",
            content = "Content",
            ownerId = UUID.randomUUID(),
            ownerName = "Owner",
            contributors = emptyList(),
            version = "1.0.0",
            status = ProposalStatus.PUBLISHED,
            stats = emptyMap(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val pagedResponse = PagedProposalSearchResponse(
            content = listOf(searchResponse),
            totalElements = 1,
            totalPages = 1,
            page = 0,
            size = 20
        )

        every { proposalSearchService.searchProposals(any()) } returns pagedResponse

        // When & Then
        mockMvc.perform(
            get("/api/v1/proposals/search")
                .param("sortBy", "title")
                .param("sortOrder", "asc")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
    }

    @Test
    fun `searchProposals should return empty results when no matches found`() {
        // Given
        val pagedResponse = PagedProposalSearchResponse(
            content = emptyList(),
            totalElements = 0,
            totalPages = 0,
            page = 0,
            size = 20
        )

        every { proposalSearchService.searchProposals(any()) } returns pagedResponse

        // When & Then
        mockMvc.perform(get("/api/v1/proposals/search").param("query", "NonExistent"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    fun `searchProposals should handle errors gracefully`() {
        // Given
        every { proposalSearchService.searchProposals(any()) } throws RuntimeException("Search failed")

        // When & Then
        mockMvc.perform(get("/api/v1/proposals/search"))
            .andExpect(status().isInternalServerError)
    }

    @Test
    fun `searchProposals should return proposals with contributors`() {
        // Given
        val proposalId = UUID.randomUUID()
        val contributorDto = ContributorDto(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            userName = "Contributor Name",
            role = "reviewer",
            status = "ACTIVE"
        )

        val searchResponse = ProposalSearchResponse(
            id = proposalId,
            title = "Proposal With Contributors",
            content = "Content",
            ownerId = UUID.randomUUID(),
            ownerName = "Owner",
            contributors = listOf(contributorDto),
            version = "1.0.0",
            status = ProposalStatus.PUBLISHED,
            stats = mapOf("views" to 100, "likes" to 50),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val pagedResponse = PagedProposalSearchResponse(
            content = listOf(searchResponse),
            totalElements = 1,
            totalPages = 1,
            page = 0,
            size = 20
        )

        every { proposalSearchService.searchProposals(any()) } returns pagedResponse

        // When & Then
        mockMvc.perform(get("/api/v1/proposals/search"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].contributors.length()").value(1))
            .andExpect(jsonPath("$.content[0].contributors[0].userName").value("Contributor Name"))
            .andExpect(jsonPath("$.content[0].contributors[0].role").value("reviewer"))
            .andExpect(jsonPath("$.content[0].stats.views").value(100))
    }

    @Test
    fun `getProposal should return proposal by ID`() {
        // Given
        val proposalId = UUID.randomUUID()
        val searchResponse = ProposalSearchResponse(
            id = proposalId,
            title = "Single Proposal",
            content = "Content",
            ownerId = UUID.randomUUID(),
            ownerName = "Owner",
            contributors = emptyList(),
            version = "1.0.0",
            status = ProposalStatus.PUBLISHED,
            stats = emptyMap(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { proposalSearchService.getProposalById(proposalId.toString()) } returns searchResponse

        // When & Then
        mockMvc.perform(get("/api/v1/proposals/$proposalId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(proposalId.toString()))
            .andExpect(jsonPath("$.title").value("Single Proposal"))
    }

    @Test
    fun `getProposal should return 404 when proposal not found`() {
        // Given
        val proposalId = UUID.randomUUID()

        every { proposalSearchService.getProposalById(proposalId.toString()) } returns null

        // When & Then
        mockMvc.perform(get("/api/v1/proposals/$proposalId"))
            .andExpect(status().isNotFound)
    }
}
