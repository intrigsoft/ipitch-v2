package com.intrigsoft.ipitch.proposalviewmanager.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ViewControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `hello endpoint should return greeting with default name`() {
        // When & Then
        mockMvc.perform(get("/api/views/hello"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Hello, World!"))
            .andExpect(jsonPath("$.service").value("proposal-view-manager"))
    }

    @Test
    fun `hello endpoint should return greeting with custom name`() {
        // When & Then
        mockMvc.perform(get("/api/views/hello").param("name", "Alice"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Hello, Alice!"))
            .andExpect(jsonPath("$.service").value("proposal-view-manager"))
    }

    @Test
    fun `status endpoint should return service status`() {
        // When & Then
        mockMvc.perform(get("/api/views/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("proposal-view-manager"))
            .andExpect(jsonPath("$.timestamp").exists())
    }
}
