package com.intrigsoft.ipitch.proposalviewmanager.service

import com.intrigsoft.ipitch.domain.ContributorStatus
import com.intrigsoft.ipitch.domain.ProposalStatus
import com.intrigsoft.ipitch.proposalviewmanager.document.ProposalDocument
import com.intrigsoft.ipitch.proposalviewmanager.dto.ProposalPublishDto
import com.intrigsoft.ipitch.proposalviewmanager.repository.ProposalDocumentRepository
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
class ProposalIndexServiceTest {

    @MockK
    private lateinit var proposalDocumentRepository: ProposalDocumentRepository

    @InjectMockKs
    private lateinit var proposalIndexService: ProposalIndexService

    private lateinit var testPublishDto: ProposalPublishDto
    private lateinit var testProposalId: UUID

    @BeforeEach
    fun setUp() {
        testProposalId = UUID.randomUUID()

        testPublishDto = ProposalPublishDto(
            id = testProposalId,
            title = "Test Proposal",
            content = "Test content",
            ownerId = UUID.randomUUID(),
            ownerName = "Test Owner",
            contributors = listOf(
                ProposalPublishDto.ContributorDto(
                    id = UUID.randomUUID(),
                    userId = UUID.randomUUID(),
                    userName = "Contributor 1",
                    role = "reviewer",
                    status = ContributorStatus.ACTIVE.name
                )
            ),
            version = "1.0.0",
            status = ProposalStatus.PUBLISHED,
            stats = mapOf("views" to 100, "likes" to 50),
            workingBranch = "proposal/$testProposalId",
            gitCommitHash = "abc123",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }

    @Test
    fun `indexProposal should save proposal document successfully`() {
        // Given
        val savedDocument = slot<ProposalDocument>()
        every { proposalDocumentRepository.save(capture(savedDocument)) } answers { firstArg() }

        // When
        proposalIndexService.indexProposal(testPublishDto)

        // Then
        verify(exactly = 1) { proposalDocumentRepository.save(any()) }

        // Verify the document structure
        assertEquals(testProposalId.toString(), savedDocument.captured.id)
        assertEquals("Test Proposal", savedDocument.captured.title)
        assertEquals("Test content", savedDocument.captured.content)
        assertEquals(testPublishDto.ownerId.toString(), savedDocument.captured.ownerId)
        assertEquals("Test Owner", savedDocument.captured.ownerName)
        assertEquals(1, savedDocument.captured.contributors.size)
        assertEquals("1.0.0", savedDocument.captured.version)
        assertEquals(ProposalStatus.PUBLISHED.name, savedDocument.captured.status)
        assertEquals("proposal/$testProposalId", savedDocument.captured.workingBranch)
        assertEquals("abc123", savedDocument.captured.gitCommitHash)
    }

    @Test
    fun `indexProposal should map contributors correctly`() {
        // Given
        val savedDocument = slot<ProposalDocument>()
        every { proposalDocumentRepository.save(capture(savedDocument)) } answers { firstArg() }

        // When
        proposalIndexService.indexProposal(testPublishDto)

        // Then
        val capturedContributor = savedDocument.captured.contributors.first()
        val expectedContributor = testPublishDto.contributors.first()

        assertEquals(expectedContributor.id.toString(), capturedContributor.id)
        assertEquals(expectedContributor.userId.toString(), capturedContributor.userId)
        assertEquals("Contributor 1", capturedContributor.userName)
        assertEquals("reviewer", capturedContributor.role)
        assertEquals(ContributorStatus.ACTIVE.name, capturedContributor.status)
    }

    @Test
    fun `indexProposal should map stats correctly`() {
        // Given
        val savedDocument = slot<ProposalDocument>()
        every { proposalDocumentRepository.save(capture(savedDocument)) } answers { firstArg() }

        // When
        proposalIndexService.indexProposal(testPublishDto)

        // Then
        assertEquals(100, savedDocument.captured.stats["views"])
        assertEquals(50, savedDocument.captured.stats["likes"])
    }

    @Test
    fun `indexProposal should handle empty contributors list`() {
        // Given
        val publishDtoWithNoContributors = testPublishDto.copy(contributors = emptyList())
        val savedDocument = slot<ProposalDocument>()
        every { proposalDocumentRepository.save(capture(savedDocument)) } answers { firstArg() }

        // When
        proposalIndexService.indexProposal(publishDtoWithNoContributors)

        // Then
        verify(exactly = 1) { proposalDocumentRepository.save(any()) }
        assertTrue(savedDocument.captured.contributors.isEmpty())
    }

    @Test
    fun `indexProposal should throw exception when repository fails`() {
        // Given
        every { proposalDocumentRepository.save(any()) } throws RuntimeException("Elasticsearch error")

        // When & Then
        assertThrows<RuntimeException> {
            proposalIndexService.indexProposal(testPublishDto)
        }
    }

    @Test
    fun `deleteProposal should delete document by ID successfully`() {
        // Given
        val proposalId = testProposalId.toString()
        every { proposalDocumentRepository.deleteById(proposalId) } just Runs

        // When
        proposalIndexService.deleteProposal(proposalId)

        // Then
        verify(exactly = 1) { proposalDocumentRepository.deleteById(proposalId) }
    }

    @Test
    fun `deleteProposal should throw exception when repository fails`() {
        // Given
        val proposalId = testProposalId.toString()
        every { proposalDocumentRepository.deleteById(proposalId) } throws RuntimeException("Delete failed")

        // When & Then
        assertThrows<RuntimeException> {
            proposalIndexService.deleteProposal(proposalId)
        }
    }

    @Test
    fun `indexProposal should handle proposal with null optional fields`() {
        // Given
        val publishDto = testPublishDto.copy(
            ownerName = null,
            workingBranch = null,
            gitCommitHash = null,
            stats = emptyMap()
        )
        val savedDocument = slot<ProposalDocument>()
        every { proposalDocumentRepository.save(capture(savedDocument)) } answers { firstArg() }

        // When
        proposalIndexService.indexProposal(publishDto)

        // Then
        verify(exactly = 1) { proposalDocumentRepository.save(any()) }
        assertNull(savedDocument.captured.ownerName)
        assertNull(savedDocument.captured.workingBranch)
        assertNull(savedDocument.captured.gitCommitHash)
        assertTrue(savedDocument.captured.stats.isEmpty())
    }

    @Test
    fun `indexProposal should replace existing document with same ID`() {
        // Given
        val savedDocument = slot<ProposalDocument>()
        every { proposalDocumentRepository.save(capture(savedDocument)) } answers { firstArg() }

        // When - index the same proposal twice (simulating version update)
        proposalIndexService.indexProposal(testPublishDto)
        val updatedPublishDto = testPublishDto.copy(
            version = "2.0.0",
            content = "Updated content"
        )
        proposalIndexService.indexProposal(updatedPublishDto)

        // Then
        verify(exactly = 2) { proposalDocumentRepository.save(any()) }
        assertEquals("2.0.0", savedDocument.captured.version)
        assertEquals("Updated content", savedDocument.captured.content)
        // Same ID ensures replacement, not duplication
        assertEquals(testProposalId.toString(), savedDocument.captured.id)
    }
}
