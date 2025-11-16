package com.example.ipitch.proposalviewmanager.api

import com.example.ipitch.common.GreetingService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ViewController {
    private val greeter = GreetingService()

    @GetMapping("/api/views/hello")
    fun hello(@RequestParam name: String = "World"): Map<String, String> =
        mapOf("message" to greeter.greet(name), "service" to "proposal-view-manager")
}
