package com.intrigsoft.ipitch.config

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt

object SecurityUtils {

    /**
     * Gets the currently authenticated user's ID from the JWT token
     */
    fun getCurrentUserId(): String {
        val authentication = SecurityContextHolder.getContext().authentication
        val jwt = authentication?.principal as? Jwt
        return jwt?.subject ?: throw IllegalStateException("No authenticated user found")
    }

    /**
     * Gets the JWT token of the currently authenticated user
     */
    fun getCurrentUserJwt(): Jwt {
        val authentication = SecurityContextHolder.getContext().authentication
        return authentication?.principal as? Jwt
            ?: throw IllegalStateException("No authenticated user found")
    }

    /**
     * Checks if there is an authenticated user
     */
    fun isAuthenticated(): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication
        return authentication != null && authentication.isAuthenticated && authentication.principal is Jwt
    }
}
