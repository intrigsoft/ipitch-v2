package com.intrigsoft.ipitch.proposalmanager.client

import com.intrigsoft.ipitch.proposalmanager.dto.response.ApiResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

/**
 * Feign client for calling proposal-view-manager internal API
 */
@FeignClient(
    name = "proposal-view-manager",
    url = "\${proposal-view-manager.url:http://localhost:8082}"
)
interface ProposalViewManagerClient {

    /**
     * Publish a proposal to the view manager for indexing
     */
    @PostMapping("/internal/api/v1/proposals/publish")
    fun publishProposal(@RequestBody publishDto: ProposalPublishDto): ApiResponse<String>

    /**
     * Delete a proposal from the view manager index
     */
    @DeleteMapping("/internal/api/v1/proposals/{proposalId}")
    fun deleteProposal(@PathVariable proposalId: String): ApiResponse<String>
}

/**
 * DTO for publishing a proposal to view manager
 */
data class ProposalPublishDto(
    val id: java.util.UUID,
    val title: String,
    val content: String,
    val ownerId: java.util.UUID,
    val ownerName: String? = null,
    val contributors: List<ContributorDto> = emptyList(),
    val version: String,
    val status: String,
    val stats: Map<String, Any> = emptyMap(),
    val workingBranch: String? = null,
    val gitCommitHash: String? = null,
    val createdAt: java.time.LocalDateTime,
    val updatedAt: java.time.LocalDateTime
)

data class ContributorDto(
    val id: java.util.UUID,
    val userId: java.util.UUID,
    val userName: String? = null,
    val role: String,
    val status: String
)
