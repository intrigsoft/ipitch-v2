package com.intrigsoft.ipitch.aiintegration.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SectorScoreTest {

    @Test
    fun `should create valid sector score`() {
        // Given & When
        val sectorScore = SectorScore("Healthcare", 8.5)

        // Then
        assertEquals("Healthcare", sectorScore.sector)
        assertEquals(8.5, sectorScore.score)
    }

    @Test
    fun `should accept minimum valid score`() {
        // Given & When
        val sectorScore = SectorScore("IT", 0.0)

        // Then
        assertEquals(0.0, sectorScore.score)
    }

    @Test
    fun `should accept maximum valid score`() {
        // Given & When
        val sectorScore = SectorScore("IT", 10.0)

        // Then
        assertEquals(10.0, sectorScore.score)
    }

    @Test
    fun `should throw exception for score below 0`() {
        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            SectorScore("IT", -1.0)
        }

        assertEquals("Score must be between 0.0 and 10.0", exception.message)
    }

    @Test
    fun `should throw exception for score above 10`() {
        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            SectorScore("IT", 11.0)
        }

        assertEquals("Score must be between 0.0 and 10.0", exception.message)
    }
}
