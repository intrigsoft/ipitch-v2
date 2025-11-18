package com.intrigsoft.ipitch.proposalviewmanager.service

import com.intrigsoft.ipitch.domain.ProposalStatus
import com.intrigsoft.ipitch.proposalviewmanager.document.ContributorDocument
import com.intrigsoft.ipitch.proposalviewmanager.document.ProposalDocument
import com.intrigsoft.ipitch.proposalviewmanager.dto.ProposalSearchRequest
import com.intrigsoft.ipitch.proposalviewmanager.repository.ProposalDocumentRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockKExtension::class)
class ProposalSearchServiceTest {

    @MockK
    private lateinit var proposalDocumentRepository: ProposalDocumentRepository

    @MockK
    private lateinit var elasticsearchOperations: ElasticsearchOperations

    @InjectMockKs
    private lateinit var proposalSearchService: ProposalSearchService

    private lateinit var testDocument: ProposalDocument
    private lateinit var testDocuments: List<ProposalDocument>
    private lateinit var testOwnerId: String

    @BeforeEach
    fun setUp() {
        testOwnerId = "test-owner-${UUID.randomUUID()}"

        testDocument = ProposalDocument(
            id = UUID.randomUUID().toString(),
            title = "Test Proposal",
            content = "Test content",
            ownerId = testOwnerId,
            ownerName = "Test Owner",
            contributors = listOf(
                ContributorDocument(
                    id = UUID.randomUUID().toString(),
                    userId = "contributor-${UUID.randomUUID()}",
                    userName = "Contributor",
                    role = "reviewer",
                    status = "ACTIVE"
                )
            ),
            version = "1.0.0",
            status = ProposalStatus.PUBLISHED.name,
            stats = mapOf("views" to 100),
            workingBranch = "proposal/test",
            gitCommitHash = "abc123",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        testDocuments = listOf(testDocument)
    }

    @Test
    fun `searchProposals should return results when searching by query`() {
        // Given
        val request = ProposalSearchRequest(
            query = "Test",
            page = 0,
            size = 10,
            sortBy = "createdAt",
            sortOrder = "desc"
        )
        val pageable = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("createdAt").descending())
        val page = PageImpl(testDocuments, pageable, testDocuments.size.toLong())

        every {
            proposalDocumentRepository.findByTitleContainingOrContentContaining(
                "Test",
                "Test",
                any()
            )
        } returns page

        // When
        val result = proposalSearchService.searchProposals(request)

        // Then
        assertEquals(1, result.content.size)
        assertEquals("Test Proposal", result.content[0].title)
        assertEquals(1L, result.totalElements)
        assertEquals(1, result.totalPages)
        assertEquals(0, result.page)
        assertEquals(10, result.size)
    }

    @Test
    fun `searchProposals should return results when filtering by owner and status`() {
        // Given
        val request = ProposalSearchRequest(
            ownerId = testOwnerId,
            status = ProposalStatus.PUBLISHED,
            page = 0,
            size = 10
        )
        val pageable = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("createdAt").descending())
        val page = PageImpl(testDocuments, pageable, testDocuments.size.toLong())

        every {
            proposalDocumentRepository.findByOwnerIdAndStatus(
                testOwnerId,
                ProposalStatus.PUBLISHED.name,
                any()
            )
        } returns page

        // When
        val result = proposalSearchService.searchProposals(request)

        // Then
        assertEquals(1, result.content.size)
        assertEquals(testOwnerId, result.content[0].ownerId)
        assertEquals(ProposalStatus.PUBLISHED, result.content[0].status)
    }

    @Test
    fun `searchProposals should return results when filtering by owner only`() {
        // Given
        val request = ProposalSearchRequest(
            ownerId = testOwnerId,
            page = 0,
            size = 10
        )
        val pageable = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("createdAt").descending())
        val page = PageImpl(testDocuments, pageable, testDocuments.size.toLong())

        every {
            proposalDocumentRepository.findByOwnerId(testOwnerId, any())
        } returns page

        // When
        val result = proposalSearchService.searchProposals(request)

        // Then
        assertEquals(1, result.content.size)
        assertEquals(testOwnerId, result.content[0].ownerId)
    }

    @Test
    fun `searchProposals should return results when filtering by status only`() {
        // Given
        val request = ProposalSearchRequest(
            status = ProposalStatus.PUBLISHED,
            page = 0,
            size = 10
        )
        val pageable = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("createdAt").descending())
        val page = PageImpl(testDocuments, pageable, testDocuments.size.toLong())

        every {
            proposalDocumentRepository.findByStatus(ProposalStatus.PUBLISHED.name, any())
        } returns page

        // When
        val result = proposalSearchService.searchProposals(request)

        // Then
        assertEquals(1, result.content.size)
        assertEquals(ProposalStatus.PUBLISHED, result.content[0].status)
    }

    @Test
    fun `searchProposals should return all results when no filters provided`() {
        // Given
        val request = ProposalSearchRequest(
            page = 0,
            size = 10
        )
        val pageable = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("createdAt").descending())
        val page = PageImpl(testDocuments, pageable, testDocuments.size.toLong())

        every { proposalDocumentRepository.findAll(any<PageRequest>()) } returns page

        // When
        val result = proposalSearchService.searchProposals(request)

        // Then
        assertEquals(1, result.content.size)
    }

    @Test
    fun `searchProposals should handle ascending sort order`() {
        // Given
        val request = ProposalSearchRequest(
            page = 0,
            size = 10,
            sortBy = "title",
            sortOrder = "asc"
        )
        val pageable = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("title").ascending())
        val page = PageImpl(testDocuments, pageable, testDocuments.size.toLong())

        every { proposalDocumentRepository.findAll(any<PageRequest>()) } returns page

        // When
        val result = proposalSearchService.searchProposals(request)

        // Then
        assertEquals(1, result.content.size)
    }

    @Test
    fun `searchProposals should handle pagination correctly`() {
        // Given
        val request = ProposalSearchRequest(
            page = 1,
            size = 5
        )
        val pageable = PageRequest.of(1, 5, org.springframework.data.domain.Sort.by("createdAt").descending())
        val page = PageImpl(emptyList<ProposalDocument>(), pageable, 10)

        every { proposalDocumentRepository.findAll(any<PageRequest>()) } returns page

        // When
        val result = proposalSearchService.searchProposals(request)

        // Then
        assertEquals(0, result.content.size)
        assertEquals(10L, result.totalElements)
        assertEquals(2, result.totalPages)
        assertEquals(1, result.page)
        assertEquals(5, result.size)
    }

    @Test
    fun `searchProposals should map contributors correctly`() {
        // Given
        val request = ProposalSearchRequest(page = 0, size = 10)
        val pageable = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("createdAt").descending())
        val page = PageImpl(testDocuments, pageable, testDocuments.size.toLong())

        every { proposalDocumentRepository.findAll(any<PageRequest>()) } returns page

        // When
        val result = proposalSearchService.searchProposals(request)

        // Then
        assertEquals(1, result.content[0].contributors.size)
        val contributor = result.content[0].contributors[0]
        assertEquals("Contributor", contributor.userName)
        assertEquals("reviewer", contributor.role)
        assertEquals("ACTIVE", contributor.status)
    }

    @Test
    fun `searchProposals should handle empty results`() {
        // Given
        val request = ProposalSearchRequest(
            query = "NonExistent",
            page = 0,
            size = 10
        )
        val pageable = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("createdAt").descending())
        val page = PageImpl(emptyList<ProposalDocument>(), pageable, 0)

        every {
            proposalDocumentRepository.findByTitleContainingOrContentContaining(
                "NonExistent",
                "NonExistent",
                any()
            )
        } returns page

        // When
        val result = proposalSearchService.searchProposals(request)

        // Then
        assertEquals(0, result.content.size)
        assertEquals(0L, result.totalElements)
        assertEquals(0, result.totalPages)
    }

    @Test
    fun `getProposalById should return proposal when it exists`() {
        // Given
        val proposalId = testDocument.id

        every { proposalDocumentRepository.findById(proposalId) } returns Optional.of(testDocument)

        // When
        val result = proposalSearchService.getProposalById(proposalId)

        // Then
        assertNotNull(result)
        assertEquals(proposalId, result?.id.toString())
        assertEquals("Test Proposal", result?.title)
        assertEquals("Test content", result?.content)
        assertEquals(testOwnerId, result?.ownerId)
        assertEquals("1.0.0", result?.version)
        assertEquals(ProposalStatus.PUBLISHED, result?.status)
    }

    @Test
    fun `getProposalById should return null when proposal does not exist`() {
        // Given
        val proposalId = UUID.randomUUID().toString()

        every { proposalDocumentRepository.findById(proposalId) } returns Optional.empty()

        // When
        val result = proposalSearchService.getProposalById(proposalId)

        // Then
        assertNull(result)
    }

    @Test
    fun `getProposalById should map all fields correctly`() {
        // Given
        val proposalId = testDocument.id

        every { proposalDocumentRepository.findById(proposalId) } returns Optional.of(testDocument)

        // When
        val result = proposalSearchService.getProposalById(proposalId)

        // Then
        assertNotNull(result)
        assertEquals("Test Owner", result?.ownerName)
        assertEquals(1, result?.contributors?.size)
        assertEquals(100, result?.stats?.get("views"))
        assertNotNull(result?.createdAt)
        assertNotNull(result?.updatedAt)
    }
}
