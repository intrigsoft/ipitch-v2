package com.example.ipitch.proposalmanager.dto.response

import java.util.*

data class PullRequestResponse(
    val pullRequestId: String,
    val proposalId: UUID,
    val contributorId: UUID,
    val sourceBranch: String,
    val targetBranch: String,
    val description: String,
    val status: String,
    val createdAt: String
)
