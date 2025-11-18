package com.intrigsoft.ipitch.usermanager.dto.request

import com.intrigsoft.ipitch.domain.UserViewPermissions
import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    @field:Size(max = 5000, message = "Description must not exceed 5000 characters")
    val description: String? = null,

    val avatarUrl: String? = null,

    val viewPermissions: UserViewPermissions? = null
)
