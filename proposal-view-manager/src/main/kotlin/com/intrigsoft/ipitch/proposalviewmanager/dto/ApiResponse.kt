package com.intrigsoft.ipitch.proposalviewmanager.dto

/**
 * Generic API response wrapper
 */
data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null
)
