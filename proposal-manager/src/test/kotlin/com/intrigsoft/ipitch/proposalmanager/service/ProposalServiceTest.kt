package com.intrigsoft.ipitch.proposalmanager.service

import com.intrigsoft.ipitch.domain.*
import com.intrigsoft.ipitch.proposalmanager.dto.request.*
import com.intrigsoft.ipitch.proposalmanager.exception.*
import com.intrigsoft.ipitch.repository.*
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
class ProposalServiceTest {

    @MockK
    private lateinit var proposalRepository: ProposalRepository

    @MockK
    private lateinit var contributorRepository: ContributorRepository

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var gitService: GitService

    @InjectMockKs
    private lateinit var proposalService: ProposalService

    private lateinit var testProposal: Proposal
    private lateinit var testContributor: Contributor
    private lateinit var testOwnerId: String
    private lateinit var testProposalId: UUID

    @BeforeEach
    fun setUp() {
        testOwnerId = "test-user-${UUID.randomUUID()}"
        testProposalId = UUID.randomUUID()

        testProposal = Proposal(
            id = testProposalId,
            title = "Test Proposal",
            content = "Test content",
            ownerId = testOwnerId,
            status = ProposalStatus.DRAFT,
            version = "0.0.1",
            stats = emptyMap(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            workingBranch = "proposal/$testProposalId",
            gitCommitHash = "abc123"
        )

        testContributor = Contributor(
            id = UUID.randomUUID(),
            userId = "contributor-user-${UUID.randomUUID()}",
            role = "contributor",
            status = ContributorStatus.ACTIVE,
            proposal = testProposal
        )
    }

    @Test
    fun `createProposal should create proposal successfully`() {
        // Given
        val request = CreateProposalRequest(
            title = "New Proposal",
            content = "New content",
            ownerId = testOwnerId
        )

        val savedProposal = testProposal.copy(id = UUID.randomUUID())
        val workingBranch = "proposal/${savedProposal.id}"
        val mockUser = User(
            userId = testOwnerId,
            userName = "Test Owner",
            email = "owner@test.com",
            status = UserStatus.ACTIVE
        )

        every { proposalRepository.save(any()) } returns savedProposal
        every { userRepository.findById(testOwnerId) } returns Optional.of(mockUser)
        every { gitService.createProposal(savedProposal.id!!, request.title, request.content, any(), any()) } returns workingBranch

        // When
        val result = proposalService.createProposal(request)

        // Then
        assertNotNull(result)
        assertEquals("New Proposal", result.title)
        assertEquals(ProposalStatus.DRAFT, result.status)
        verify(exactly = 2) { proposalRepository.save(any()) }
        verify(exactly = 1) { gitService.createProposal(savedProposal.id!!, request.title, request.content, any(), any()) }
    }

    @Test
    fun `createProposal should throw exception when Git operation fails`() {
        // Given
        val request = CreateProposalRequest(
            title = "New Proposal",
            content = "New content",
            ownerId = testOwnerId
        )

        val savedProposal = testProposal.copy(id = UUID.randomUUID())
        val mockUser = User(
            userId = testOwnerId,
            userName = "Test Owner",
            email = "owner@test.com",
            status = UserStatus.ACTIVE
        )

        every { proposalRepository.save(any()) } returns savedProposal
        every { userRepository.findById(testOwnerId) } returns Optional.of(mockUser)
        every { gitService.createProposal(any(), any(), any(), any(), any()) } throws RuntimeException("Git error")

        // When & Then
        assertThrows<RuntimeException> {
            proposalService.createProposal(request)
        }
    }

    @Test
    fun `updateProposalMetadata should update status and stats`() {
        // Given
        val request = UpdateProposalMetadataRequest(
            status = ProposalStatus.PUBLISHED,
            stats = mapOf("views" to 100)
        )

        every { proposalRepository.findById(testProposalId) } returns Optional.of(testProposal)
        every { proposalRepository.save(any()) } answers { firstArg() }

        // When
        val result = proposalService.updateProposalMetadata(testProposalId, request)

        // Then
        assertNotNull(result)
        assertEquals(ProposalStatus.PUBLISHED, result.status)
        verify(exactly = 1) { proposalRepository.save(any()) }
    }

    @Test
    fun `updateProposalMetadata should throw ProposalNotFoundException when proposal not found`() {
        // Given
        val request = UpdateProposalMetadataRequest(status = ProposalStatus.PUBLISHED, stats = null)

        every { proposalRepository.findById(testProposalId) } returns Optional.empty()

        // When & Then
        assertThrows<ProposalNotFoundException> {
            proposalService.updateProposalMetadata(testProposalId, request)
        }
    }

    @Test
    fun `addContributor should add contributor successfully`() {
        // Given
        val userId = "reviewer-user-${UUID.randomUUID()}"
        val request = AddContributorRequest(
            userId = userId,
            role = "reviewer"
        )

        val savedContributor = testContributor.copy(id = UUID.randomUUID(), userId = userId, role = "reviewer")

        every { proposalRepository.findById(testProposalId) } returns Optional.of(testProposal)
        every { contributorRepository.findByProposalAndUserId(testProposal, userId) } returns null
        every { contributorRepository.save(any()) } returns savedContributor
        every { gitService.createContributorBranch(testProposalId, savedContributor.id!!) } returns "contributor-branch"

        // When
        val result = proposalService.addContributor(testProposalId, request)

        // Then
        assertNotNull(result)
        assertEquals(userId, result.userId)
        assertEquals("reviewer", result.role)
        assertEquals(ContributorStatus.ACTIVE, result.status)
        verify(exactly = 1) { contributorRepository.save(any()) }
        verify(exactly = 1) { gitService.createContributorBranch(testProposalId, savedContributor.id!!) }
    }

    @Test
    fun `addContributor should throw InvalidOperationException when contributor already exists`() {
        // Given
        val userId = "existing-user-${UUID.randomUUID()}"
        val request = AddContributorRequest(
            userId = userId,
            role = "reviewer"
        )

        every { proposalRepository.findById(testProposalId) } returns Optional.of(testProposal)
        every { contributorRepository.findByProposalAndUserId(testProposal, userId) } returns testContributor

        // When & Then
        assertThrows<InvalidOperationException> {
            proposalService.addContributor(testProposalId, request)
        }
        verify(exactly = 0) { contributorRepository.save(any()) }
    }

    @Test
    fun `removeContributor should remove contributor successfully`() {
        // Given
        val contributorId = testContributor.id!!

        every { proposalRepository.findById(testProposalId) } returns Optional.of(testProposal)
        every { contributorRepository.findById(contributorId) } returns Optional.of(testContributor)
        every { contributorRepository.delete(testContributor) } just Runs

        // When
        val result = proposalService.removeContributor(testProposalId, contributorId)

        // Then
        assertTrue(result.success)
        assertEquals("Contributor removed successfully", result.message)
        verify(exactly = 1) { contributorRepository.delete(testContributor) }
    }

    @Test
    fun `removeContributor should throw InvalidOperationException when contributor doesn't belong to proposal`() {
        // Given
        val contributorId = testContributor.id!!
        val otherProposal = testProposal.copy(id = UUID.randomUUID())
        val contributorFromOtherProposal = testContributor.copy(proposal = otherProposal)

        every { proposalRepository.findById(testProposalId) } returns Optional.of(testProposal)
        every { contributorRepository.findById(contributorId) } returns Optional.of(contributorFromOtherProposal)

        // When & Then
        assertThrows<InvalidOperationException> {
            proposalService.removeContributor(testProposalId, contributorId)
        }
        verify(exactly = 0) { contributorRepository.delete(any()) }
    }

    @Test
    fun `updateContent should update content successfully when contributor is authorized`() {
        // Given
        val request = UpdateContentRequest(
            contributorId = testContributor.id!!,
            content = "Updated content",
            commitMessage = "Update content"
        )

        val commitHash = "new-commit-hash"
        val mockUser = User(
            userId = testContributor.userId,
            userName = "Test Contributor",
            email = "contributor@test.com",
            status = UserStatus.ACTIVE
        )

        every { proposalRepository.findById(testProposalId) } returns Optional.of(testProposal)
        every { contributorRepository.findById(request.contributorId) } returns Optional.of(testContributor)
        every { userRepository.findById(testContributor.userId) } returns Optional.of(mockUser)
        every {
            gitService.updateContent(
                proposalId = testProposalId,
                contributorId = testContributor.id!!,
                content = request.content,
                commitMessage = request.commitMessage,
                authorName = any(),
                authorEmail = any()
            )
        } returns commitHash
        every { proposalRepository.save(any()) } answers { firstArg() }

        // When
        val result = proposalService.updateContent(testProposalId, request)

        // Then
        assertNotNull(result)
        assertEquals("Updated content", result.content)
        verify(exactly = 1) { proposalRepository.save(any()) }
        verify(exactly = 1) { gitService.updateContent(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `updateContent should throw UnauthorizedOperationException when contributor doesn't belong to proposal`() {
        // Given
        val otherProposal = testProposal.copy(id = UUID.randomUUID())
        val contributorFromOtherProposal = testContributor.copy(proposal = otherProposal)

        val request = UpdateContentRequest(
            contributorId = contributorFromOtherProposal.id!!,
            content = "Updated content",
            commitMessage = "Update content"
        )

        every { proposalRepository.findById(testProposalId) } returns Optional.of(testProposal)
        every { contributorRepository.findById(request.contributorId) } returns Optional.of(contributorFromOtherProposal)

        // When & Then
        assertThrows<UnauthorizedOperationException> {
            proposalService.updateContent(testProposalId, request)
        }
    }

    @Test
    fun `updateContent should throw UnauthorizedOperationException when contributor is not active`() {
        // Given
        val suspendedContributor = testContributor.copy(status = ContributorStatus.SUSPENDED)

        val request = UpdateContentRequest(
            contributorId = suspendedContributor.id!!,
            content = "Updated content",
            commitMessage = "Update content"
        )

        every { proposalRepository.findById(testProposalId) } returns Optional.of(testProposal)
        every { contributorRepository.findById(request.contributorId) } returns Optional.of(suspendedContributor)

        // When & Then
        assertThrows<UnauthorizedOperationException> {
            proposalService.updateContent(testProposalId, request)
        }
    }

    @Test
    fun `getProposal should return proposal when it exists`() {
        // Given
        every { proposalRepository.findById(testProposalId) } returns Optional.of(testProposal)

        // When
        val result = proposalService.getProposal(testProposalId)

        // Then
        assertNotNull(result)
        assertEquals(testProposalId, result.id)
        assertEquals("Test Proposal", result.title)
    }

    @Test
    fun `getProposal should throw ProposalNotFoundException when proposal doesn't exist`() {
        // Given
        every { proposalRepository.findById(testProposalId) } returns Optional.empty()

        // When & Then
        assertThrows<ProposalNotFoundException> {
            proposalService.getProposal(testProposalId)
        }
    }
}
