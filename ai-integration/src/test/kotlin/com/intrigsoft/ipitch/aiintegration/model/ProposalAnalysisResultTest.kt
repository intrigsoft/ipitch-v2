package com.intrigsoft.ipitch.aiintegration.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

class ProposalAnalysisResultTest {

    @Test
    fun `should create valid proposal analysis result`() {
        // Given
        val proposalId = UUID.randomUUID()
        val sectorScores = listOf(
            SectorScore("Healthcare", 10.0),
            SectorScore("IT", 6.5)
        )

        // When
        val result = ProposalAnalysisResult(
            proposalId = proposalId,
            summary = "Test summary",
            clarityScore = 8.5,
            sectorScores = sectorScores,
            model = "gpt-4-turbo-preview",
            provider = AIProvider.OPENAI
        )

        // Then
        assertEquals(proposalId, result.proposalId)
        assertEquals("Test summary", result.summary)
        assertEquals(8.5, result.clarityScore)
        assertEquals(2, result.sectorScores.size)
        assertEquals("gpt-4-turbo-preview", result.model)
        assertEquals(AIProvider.OPENAI, result.provider)
    }

    @Test
    fun `should accept minimum clarity score`() {
        // Given
        val proposalId = UUID.randomUUID()

        // When
        val result = ProposalAnalysisResult(
            proposalId = proposalId,
            summary = "Test",
            clarityScore = 0.0,
            sectorScores = emptyList(),
            model = "test",
            provider = AIProvider.OPENAI
        )

        // Then
        assertEquals(0.0, result.clarityScore)
    }

    @Test
    fun `should accept maximum clarity score`() {
        // Given
        val proposalId = UUID.randomUUID()

        // When
        val result = ProposalAnalysisResult(
            proposalId = proposalId,
            summary = "Test",
            clarityScore = 10.0,
            sectorScores = emptyList(),
            model = "test",
            provider = AIProvider.OPENAI
        )

        // Then
        assertEquals(10.0, result.clarityScore)
    }

    @Test
    fun `should throw exception for clarity score below 0`() {
        // Given
        val proposalId = UUID.randomUUID()

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            ProposalAnalysisResult(
                proposalId = proposalId,
                summary = "Test",
                clarityScore = -1.0,
                sectorScores = emptyList(),
                model = "test",
                provider = AIProvider.OPENAI
            )
        }

        assertEquals("Clarity score must be between 0.0 and 10.0", exception.message)
    }

    @Test
    fun `should throw exception for clarity score above 10`() {
        // Given
        val proposalId = UUID.randomUUID()

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            ProposalAnalysisResult(
                proposalId = proposalId,
                summary = "Test",
                clarityScore = 11.0,
                sectorScores = emptyList(),
                model = "test",
                provider = AIProvider.OPENAI
            )
        }

        assertEquals("Clarity score must be between 0.0 and 10.0", exception.message)
    }
}
