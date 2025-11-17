package com.intrigsoft.ipitch.interactionmanager.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.intrigsoft.ipitch.domain.Vote
import com.intrigsoft.ipitch.domain.VoteTargetType
import com.intrigsoft.ipitch.domain.VoteType
import com.intrigsoft.ipitch.interactionmanager.dto.request.CreateVoteRequest
import com.intrigsoft.ipitch.repository.VoteRepository
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
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VoteControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var voteRepository: VoteRepository

    private lateinit var testUserId: UUID
    private lateinit var testTargetId: UUID

    @BeforeEach
    fun setUp() {
        testUserId = UUID.randomUUID()
        testTargetId = UUID.randomUUID()
        voteRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        voteRepository.deleteAll()
    }

    @Test
    fun `createOrUpdateVote should create new vote successfully`() {
        // Given
        val request = CreateVoteRequest(
            userId = testUserId,
            targetType = VoteTargetType.PROPOSAL,
            targetId = testTargetId,
            voteType = VoteType.UP
        )

        // When & Then
        mockMvc.perform(
            post("/api/votes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userId").value(testUserId.toString()))
            .andExpect(jsonPath("$.data.targetType").value("PROPOSAL"))
            .andExpect(jsonPath("$.data.targetId").value(testTargetId.toString()))
            .andExpect(jsonPath("$.data.voteType").value("UP"))
    }

    @Test
    fun `createOrUpdateVote should update existing vote`() {
        // Given - create initial vote
        val initialVote = Vote(
            userId = testUserId,
            targetType = VoteTargetType.PROPOSAL,
            targetId = testTargetId,
            voteType = VoteType.UP
        )
        voteRepository.save(initialVote)

        val updateRequest = CreateVoteRequest(
            userId = testUserId,
            targetType = VoteTargetType.PROPOSAL,
            targetId = testTargetId,
            voteType = VoteType.DOWN
        )

        // When & Then
        mockMvc.perform(
            post("/api/votes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.voteType").value("DOWN"))
    }

    @Test
    fun `createOrUpdateVote should validate request fields`() {
        // Given - invalid request (missing userId)
        val invalidRequest = mapOf(
            "targetType" to "PROPOSAL",
            "targetId" to testTargetId.toString(),
            "voteType" to "UP"
        )

        // When & Then
        mockMvc.perform(
            post("/api/votes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `deleteVote should delete vote successfully when user is authorized`() {
        // Given
        val vote = Vote(
            userId = testUserId,
            targetType = VoteTargetType.PROPOSAL,
            targetId = testTargetId,
            voteType = VoteType.UP
        )
        val savedVote = voteRepository.save(vote)

        // When & Then
        mockMvc.perform(
            delete("/api/votes/${savedVote.id}")
                .param("userId", testUserId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Vote deleted successfully"))
    }

    @Test
    fun `deleteVote should return 403 when user is not authorized`() {
        // Given
        val vote = Vote(
            userId = testUserId,
            targetType = VoteTargetType.PROPOSAL,
            targetId = testTargetId,
            voteType = VoteType.UP
        )
        val savedVote = voteRepository.save(vote)
        val unauthorizedUserId = UUID.randomUUID()

        // When & Then
        mockMvc.perform(
            delete("/api/votes/${savedVote.id}")
                .param("userId", unauthorizedUserId.toString())
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `getVote should return vote when it exists`() {
        // Given
        val vote = Vote(
            userId = testUserId,
            targetType = VoteTargetType.PROPOSAL,
            targetId = testTargetId,
            voteType = VoteType.UP
        )
        val savedVote = voteRepository.save(vote)

        // When & Then
        mockMvc.perform(get("/api/votes/${savedVote.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(savedVote.id.toString()))
            .andExpect(jsonPath("$.data.voteType").value("UP"))
    }

    @Test
    fun `getVote should return 404 when vote does not exist`() {
        // Given
        val nonExistentId = UUID.randomUUID()

        // When & Then
        mockMvc.perform(get("/api/votes/$nonExistentId"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `getUserVote should return user's vote when it exists`() {
        // Given
        val vote = Vote(
            userId = testUserId,
            targetType = VoteTargetType.PROPOSAL,
            targetId = testTargetId,
            voteType = VoteType.UP
        )
        voteRepository.save(vote)

        // When & Then
        mockMvc.perform(
            get("/api/votes/user")
                .param("userId", testUserId.toString())
                .param("targetType", "PROPOSAL")
                .param("targetId", testTargetId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.voteType").value("UP"))
    }

    @Test
    fun `getUserVote should return null when user has not voted`() {
        // When & Then
        mockMvc.perform(
            get("/api/votes/user")
                .param("userId", testUserId.toString())
                .param("targetType", "PROPOSAL")
                .param("targetId", testTargetId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isEmpty)
    }

    @Test
    fun `getVoteStats should return correct statistics`() {
        // Given - create multiple votes
        voteRepository.save(
            Vote(
                userId = UUID.randomUUID(),
                targetType = VoteTargetType.PROPOSAL,
                targetId = testTargetId,
                voteType = VoteType.UP
            )
        )
        voteRepository.save(
            Vote(
                userId = UUID.randomUUID(),
                targetType = VoteTargetType.PROPOSAL,
                targetId = testTargetId,
                voteType = VoteType.UP
            )
        )
        voteRepository.save(
            Vote(
                userId = UUID.randomUUID(),
                targetType = VoteTargetType.PROPOSAL,
                targetId = testTargetId,
                voteType = VoteType.DOWN
            )
        )

        // When & Then
        mockMvc.perform(
            get("/api/votes/stats")
                .param("targetType", "PROPOSAL")
                .param("targetId", testTargetId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.upvotes").value(2))
            .andExpect(jsonPath("$.data.downvotes").value(1))
            .andExpect(jsonPath("$.data.score").value(1))
    }

    @Test
    fun `getVotesByTarget should return all votes for target`() {
        // Given
        voteRepository.save(
            Vote(
                userId = UUID.randomUUID(),
                targetType = VoteTargetType.PROPOSAL,
                targetId = testTargetId,
                voteType = VoteType.UP
            )
        )
        voteRepository.save(
            Vote(
                userId = UUID.randomUUID(),
                targetType = VoteTargetType.PROPOSAL,
                targetId = testTargetId,
                voteType = VoteType.DOWN
            )
        )

        // When & Then
        mockMvc.perform(
            get("/api/votes")
                .param("targetType", "PROPOSAL")
                .param("targetId", testTargetId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(2))
    }

    @Test
    fun `removeVote should remove user's vote`() {
        // Given
        val vote = Vote(
            userId = testUserId,
            targetType = VoteTargetType.PROPOSAL,
            targetId = testTargetId,
            voteType = VoteType.UP
        )
        voteRepository.save(vote)

        // When & Then
        mockMvc.perform(
            delete("/api/votes")
                .param("userId", testUserId.toString())
                .param("targetType", "PROPOSAL")
                .param("targetId", testTargetId.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        // Verify vote was deleted
        val remainingVote = voteRepository.findByUserIdAndTargetTypeAndTargetId(
            testUserId,
            VoteTargetType.PROPOSAL,
            testTargetId
        )
        assert(remainingVote == null)
    }
}
