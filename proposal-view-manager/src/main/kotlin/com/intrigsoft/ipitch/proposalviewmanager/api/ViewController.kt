package com.intrigsoft.ipitch.proposalviewmanager.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/views")
class ViewController {

    @GetMapping("/hello")
    fun hello(@RequestParam(defaultValue = "World") name: String): Map<String, String> =
        mapOf(
            "message" to "Hello, $name!",
            "service" to "proposal-view-manager"
        )

    @GetMapping("/status")
    fun status(): Map<String, Any> =
        mapOf(
            "status" to "UP",
            "service" to "proposal-view-manager",
            "timestamp" to System.currentTimeMillis()
        )
}
