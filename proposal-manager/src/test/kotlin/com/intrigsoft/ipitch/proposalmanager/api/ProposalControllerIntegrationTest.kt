package com.intrigsoft.ipitch.proposalmanager.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.intrigsoft.ipitch.domain.Contributor
import com.intrigsoft.ipitch.domain.ContributorStatus
import com.intrigsoft.ipitch.domain.Proposal
import com.intrigsoft.ipitch.domain.ProposalStatus
import com.intrigsoft.ipitch.proposalmanager.dto.request.AddContributorRequest
import com.intrigsoft.ipitch.proposalmanager.dto.request.CreateProposalRequest
import com.intrigsoft.ipitch.proposalmanager.dto.request.UpdateProposalMetadataRequest
import com.intrigsoft.ipitch.repository.ContributorRepository
import com.intrigsoft.ipitch.repository.ProposalRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.io.File
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProposalControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var proposalRepository: ProposalRepository

    @Autowired
    private lateinit var contributorRepository: ContributorRepository

    private lateinit var testOwnerId: UUID
    private lateinit var gitRepoDir: File

    @BeforeEach
    fun setUp() {
        testOwnerId = UUID.randomUUID()
        proposalRepository.deleteAll()
        contributorRepository.deleteAll()

        // Setup test git directory
        gitRepoDir = File(System.getProperty("java.io.tmpdir"), "test-proposal-git-repo")
        if (gitRepoDir.exists()) {
            gitRepoDir.deleteRecursively()
        }
        gitRepoDir.mkdirs()
    }

    @AfterEach
    fun tearDown() {
        proposalRepository.deleteAll()
        contributorRepository.deleteAll()

        // Cleanup git directory
        if (gitRepoDir.exists()) {
            gitRepoDir.deleteRecursively()
        }
    }

    @Test
    fun `createProposal should create new proposal successfully`() {
        // Given
        val request = CreateProposalRequest(
            title = "Integration Test Proposal",
            content = "This is a test proposal content",
            ownerId = testOwnerId
        )

        // When & Then
        mockMvc.perform(
            post("/api/proposals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.title").value("Integration Test Proposal"))
            .andExpect(jsonPath("$.data.ownerId").value(testOwnerId.toString()))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andExpect(jsonPath("$.data.workingBranch").exists())
    }

    @Test
    fun `createProposal should validate required fields`() {
        // Given - request with missing title
        val invalidRequest = mapOf(
            "content" to "Content without title",
            "ownerId" to testOwnerId.toString()
        )

        // When & Then
        mockMvc.perform(
            post("/api/proposals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `updateProposalMetadata should update proposal status`() {
        // Given - create proposal first
        val proposal = Proposal(
            title = "Test Proposal",
            content = "Test content",
            ownerId = testOwnerId,
            status = ProposalStatus.DRAFT,
            workingBranch = "proposal/test"
        )
        val savedProposal = proposalRepository.save(proposal)

        val updateRequest = UpdateProposalMetadataRequest(
            status = ProposalStatus.PUBLISHED,
            stats = mapOf("views" to 100, "likes" to 50)
        )

        // When & Then
        mockMvc.perform(
            put("/api/proposals/${savedProposal.id}/metadata")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
    }

    @Test
    fun `updateProposalMetadata should return 404 when proposal not found`() {
        // Given
        val nonExistentId = UUID.randomUUID()
        val updateRequest = UpdateProposalMetadataRequest(
            status = ProposalStatus.PUBLISHED
        )

        // When & Then
        mockMvc.perform(
            put("/api/proposals/$nonExistentId/metadata")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `addContributor should add contributor to proposal successfully`() {
        // Given - create proposal first
        val proposal = Proposal(
            title = "Test Proposal",
            content = "Test content",
            ownerId = testOwnerId,
            status = ProposalStatus.DRAFT,
            workingBranch = "proposal/test"
        )
        val savedProposal = proposalRepository.save(proposal)

        val contributorRequest = AddContributorRequest(
            userId = UUID.randomUUID(),
            role = "reviewer"
        )

        // When & Then
        mockMvc.perform(
            post("/api/proposals/${savedProposal.id}/contributors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(contributorRequest))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userId").value(contributorRequest.userId.toString()))
            .andExpect(jsonPath("$.data.role").value("reviewer"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
    }

    @Test
    fun `addContributor should return 409 when contributor already exists`() {
        // Given - create proposal and contributor
        val proposal = Proposal(
            title = "Test Proposal",
            content = "Test content",
            ownerId = testOwnerId,
            status = ProposalStatus.DRAFT,
            workingBranch = "proposal/test"
        )
        val savedProposal = proposalRepository.save(proposal)

        val contributorUserId = UUID.randomUUID()
        val existingContributor = Contributor(
            userId = contributorUserId,
            role = "reviewer",
            status = ContributorStatus.ACTIVE,
            proposal = savedProposal
        )
        contributorRepository.save(existingContributor)

        val contributorRequest = AddContributorRequest(
            userId = contributorUserId,
            role = "editor"
        )

        // When & Then
        mockMvc.perform(
            post("/api/proposals/${savedProposal.id}/contributors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(contributorRequest))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `removeContributor should remove contributor successfully`() {
        // Given - create proposal and contributor
        val proposal = Proposal(
            title = "Test Proposal",
            content = "Test content",
            ownerId = testOwnerId,
            status = ProposalStatus.DRAFT,
            workingBranch = "proposal/test"
        )
        val savedProposal = proposalRepository.save(proposal)

        val contributor = Contributor(
            userId = UUID.randomUUID(),
            role = "reviewer",
            status = ContributorStatus.ACTIVE,
            proposal = savedProposal
        )
        val savedContributor = contributorRepository.save(contributor)

        // When & Then
        mockMvc.perform(
            delete("/api/proposals/${savedProposal.id}/contributors/${savedContributor.id}")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Contributor removed successfully"))
    }

    @Test
    fun `getProposal should return proposal when it exists`() {
        // Given
        val proposal = Proposal(
            title = "Test Proposal",
            content = "Test content",
            ownerId = testOwnerId,
            status = ProposalStatus.DRAFT,
            workingBranch = "proposal/test"
        )
        val savedProposal = proposalRepository.save(proposal)

        // When & Then
        mockMvc.perform(get("/api/proposals/${savedProposal.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(savedProposal.id.toString()))
            .andExpect(jsonPath("$.data.title").value("Test Proposal"))
    }

    @Test
    fun `getProposal should return 404 when proposal not found`() {
        // Given
        val nonExistentId = UUID.randomUUID()

        // When & Then
        mockMvc.perform(get("/api/proposals/$nonExistentId"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `getProposalsByOwner should return all proposals for owner`() {
        // Given - create multiple proposals
        proposalRepository.save(
            Proposal(
                title = "Proposal 1",
                content = "Content 1",
                ownerId = testOwnerId,
                status = ProposalStatus.DRAFT,
                workingBranch = "proposal/test1"
            )
        )
        proposalRepository.save(
            Proposal(
                title = "Proposal 2",
                content = "Content 2",
                ownerId = testOwnerId,
                status = ProposalStatus.PUBLISHED,
                workingBranch = "proposal/test2"
            )
        )

        // Create proposal with different owner (should not be returned)
        proposalRepository.save(
            Proposal(
                title = "Other Proposal",
                content = "Other Content",
                ownerId = UUID.randomUUID(),
                status = ProposalStatus.DRAFT,
                workingBranch = "proposal/test3"
            )
        )

        // When & Then
        mockMvc.perform(get("/api/proposals/owner/$testOwnerId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(2))
    }

    @Test
    fun `getAllProposals should return all proposals`() {
        // Given - create proposals
        proposalRepository.save(
            Proposal(
                title = "Proposal 1",
                content = "Content 1",
                ownerId = testOwnerId,
                status = ProposalStatus.DRAFT,
                workingBranch = "proposal/test1"
            )
        )
        proposalRepository.save(
            Proposal(
                title = "Proposal 2",
                content = "Content 2",
                ownerId = UUID.randomUUID(),
                status = ProposalStatus.PUBLISHED,
                workingBranch = "proposal/test2"
            )
        )

        // When & Then
        mockMvc.perform(get("/api/proposals"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(2))
    }
}
