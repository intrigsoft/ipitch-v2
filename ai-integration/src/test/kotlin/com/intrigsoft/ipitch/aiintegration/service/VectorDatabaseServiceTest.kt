package com.intrigsoft.ipitch.aiintegration.service

import com.intrigsoft.ipitch.aiintegration.config.AIProperties
import com.intrigsoft.ipitch.aiintegration.config.VectorDatabaseProperties
import com.intrigsoft.ipitch.aiintegration.model.EmbeddingVector
import com.intrigsoft.ipitch.aiintegration.repository.EmbeddingRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class VectorDatabaseServiceTest {

    private lateinit var embeddingRepository: EmbeddingRepository
    private lateinit var aiServiceFactory: AIServiceFactory
    private lateinit var vectorDbProperties: VectorDatabaseProperties
    private lateinit var aiProperties: AIProperties
    private lateinit var vectorDatabaseService: VectorDatabaseService
    private lateinit var mockAIService: AIService

    @BeforeEach
    fun setup() {
        embeddingRepository = mockk()
        aiServiceFactory = mockk()
        mockAIService = mockk()
        vectorDbProperties = VectorDatabaseProperties(embeddingDimension = 1536, similarityFunction = "cosine")
        aiProperties = AIProperties()

        every { aiServiceFactory.getAIService() } returns mockAIService

        vectorDatabaseService = VectorDatabaseService(
            embeddingRepository,
            aiServiceFactory,
            vectorDbProperties,
            aiProperties
        )
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `storeEmbedding should generate and save embedding`() = runBlocking {
        // Given
        val entityType = "PROPOSAL"
        val entityId = UUID.randomUUID()
        val text = "Test proposal content"
        val model = "text-embedding-3-large"
        val mockEmbedding = FloatArray(1536) { it.toFloat() }

        every { embeddingRepository.findByEntityTypeAndEntityId(entityType, entityId) } returns null
        coEvery { mockAIService.generateEmbedding(text) } returns mockEmbedding
        every { embeddingRepository.save(any()) } answers { firstArg() }

        // When
        val result = vectorDatabaseService.storeEmbedding(entityType, entityId, text, model)

        // Then
        assertNotNull(result)
        assertEquals(entityType, result.entityType)
        assertEquals(entityId, result.entityId)
        assertEquals(1536, result.dimension)
        assertEquals(model, result.model)

        verify { embeddingRepository.findByEntityTypeAndEntityId(entityType, entityId) }
        coVerify { mockAIService.generateEmbedding(text) }
        verify { embeddingRepository.save(any()) }
    }

    @Test
    fun `storeEmbedding should delete existing embedding before creating new one`() = runBlocking {
        // Given
        val entityType = "PROPOSAL"
        val entityId = UUID.randomUUID()
        val text = "Test proposal content"
        val model = "text-embedding-3-large"
        val mockEmbedding = FloatArray(1536) { it.toFloat() }

        val existingEmbedding = EmbeddingVector(
            id = UUID.randomUUID(),
            entityType = entityType,
            entityId = entityId,
            embedding = FloatArray(1536),
            model = "old-model",
            dimension = 1536
        )

        every { embeddingRepository.findByEntityTypeAndEntityId(entityType, entityId) } returns existingEmbedding
        every { embeddingRepository.delete(existingEmbedding) } just Runs
        coEvery { mockAIService.generateEmbedding(text) } returns mockEmbedding
        every { embeddingRepository.save(any()) } answers { firstArg() }

        // When
        vectorDatabaseService.storeEmbedding(entityType, entityId, text, model)

        // Then
        verify { embeddingRepository.delete(existingEmbedding) }
        verify { embeddingRepository.save(any()) }
    }

    @Test
    fun `getEmbedding should return embedding if exists`() {
        // Given
        val entityType = "PROPOSAL"
        val entityId = UUID.randomUUID()
        val embedding = EmbeddingVector(
            id = UUID.randomUUID(),
            entityType = entityType,
            entityId = entityId,
            embedding = FloatArray(1536),
            model = "test-model",
            dimension = 1536
        )

        every { embeddingRepository.findByEntityTypeAndEntityId(entityType, entityId) } returns embedding

        // When
        val result = vectorDatabaseService.getEmbedding(entityType, entityId)

        // Then
        assertNotNull(result)
        assertEquals(embedding.id, result?.id)
        assertEquals(entityType, result?.entityType)
        assertEquals(entityId, result?.entityId)
    }

    @Test
    fun `getEmbedding should return null if not exists`() {
        // Given
        val entityType = "PROPOSAL"
        val entityId = UUID.randomUUID()

        every { embeddingRepository.findByEntityTypeAndEntityId(entityType, entityId) } returns null

        // When
        val result = vectorDatabaseService.getEmbedding(entityType, entityId)

        // Then
        assertNull(result)
    }

    @Test
    fun `deleteEmbedding should delete embedding`() {
        // Given
        val entityType = "PROPOSAL"
        val entityId = UUID.randomUUID()

        every { embeddingRepository.deleteByEntityTypeAndEntityId(entityType, entityId) } just Runs

        // When
        vectorDatabaseService.deleteEmbedding(entityType, entityId)

        // Then
        verify { embeddingRepository.deleteByEntityTypeAndEntityId(entityType, entityId) }
    }

    @Test
    fun `hasEmbedding should return true if embedding exists`() {
        // Given
        val entityType = "PROPOSAL"
        val entityId = UUID.randomUUID()
        val embedding = EmbeddingVector(
            id = UUID.randomUUID(),
            entityType = entityType,
            entityId = entityId,
            embedding = FloatArray(1536),
            model = "test-model",
            dimension = 1536
        )

        every { embeddingRepository.findByEntityTypeAndEntityId(entityType, entityId) } returns embedding

        // When
        val result = vectorDatabaseService.hasEmbedding(entityType, entityId)

        // Then
        assertTrue(result)
    }

    @Test
    fun `hasEmbedding should return false if embedding does not exist`() {
        // Given
        val entityType = "PROPOSAL"
        val entityId = UUID.randomUUID()

        every { embeddingRepository.findByEntityTypeAndEntityId(entityType, entityId) } returns null

        // When
        val result = vectorDatabaseService.hasEmbedding(entityType, entityId)

        // Then
        assertFalse(result)
    }
}
