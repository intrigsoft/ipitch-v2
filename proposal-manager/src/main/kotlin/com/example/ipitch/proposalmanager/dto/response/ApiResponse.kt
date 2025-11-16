package com.example.ipitch.proposalmanager.dto.response

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null
)
