package com.intrigsoft.ipitch.usermanager.dto.request

import com.intrigsoft.ipitch.domain.UserViewPermissions
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    @field:Size(max = 5000, message = "Description must not exceed 5000 characters")
    val description: String? = null,

    val avatarUrl: String? = null,

    val viewPermissions: UserViewPermissions? = null,

    // Git user configuration for commit attribution
    @field:Size(max = 100, message = "Git username must not exceed 100 characters")
    val gitUsername: String? = null,

    @field:Email(message = "Git email must be a valid email address")
    val gitEmail: String? = null
)
