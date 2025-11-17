package com.intrigsoft.ipitch.proposalmanager.service

/**
 * Result of a revert operation
 */
data class RevertResult(
    val success: Boolean,
    val message: String,
    val previousVersion: String?,
    val proposalData: ProposalVersionData?
)

/**
 * Proposal data from a specific version
 */
data class ProposalVersionData(
    val title: String,
    val content: String,
    val version: String,
    val commitHash: String
)
