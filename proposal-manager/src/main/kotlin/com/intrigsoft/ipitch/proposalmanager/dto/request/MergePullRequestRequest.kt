package com.intrigsoft.ipitch.proposalmanager.dto.request

import jakarta.validation.constraints.NotBlank

data class MergePullRequestRequest(
    @field:NotBlank(message = "Pull request ID is required")
    val pullRequestId: String,

    val commitMessage: String? = null
)
