package com.intrigsoft.ipitch.usermanager.config

import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class KeycloakConfig {

    @Value("\${keycloak.auth-server-url}")
    private lateinit var authServerUrl: String

    @Value("\${keycloak.realm}")
    private lateinit var realm: String

    @Value("\${keycloak.admin.username}")
    private lateinit var adminUsername: String

    @Value("\${keycloak.admin.password}")
    private lateinit var adminPassword: String

    @Bean
    fun keycloakAdminClient(): Keycloak {
        return KeycloakBuilder.builder()
            .serverUrl(authServerUrl)
            .realm("master")
            .grantType(OAuth2Constants.PASSWORD)
            .username(adminUsername)
            .password(adminPassword)
            .clientId("admin-cli")
            .build()
    }
}
