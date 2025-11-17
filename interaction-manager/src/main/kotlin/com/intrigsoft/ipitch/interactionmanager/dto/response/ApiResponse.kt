package com.intrigsoft.ipitch.interactionmanager.dto.response

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null
) {
    companion object {
        fun <T> success(message: String, data: T? = null): ApiResponse<T> {
            return ApiResponse(success = true, message = message, data = data)
        }

        fun <T> error(message: String): ApiResponse<T> {
            return ApiResponse(success = false, message = message, data = null)
        }
    }
}
