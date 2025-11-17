package com.intrigsoft.ipitch.interactionmanager

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
@ComponentScan(basePackages = ["com.intrigsoft.ipitch"])
@EntityScan(basePackages = ["com.intrigsoft.ipitch.domain"])
@EnableJpaRepositories(basePackages = ["com.intrigsoft.ipitch.repository", "com.intrigsoft.ipitch.interactionmanager.search"])
class InteractionManagerApplication

fun main(args: Array<String>) {
    runApplication<InteractionManagerApplication>(*args)
}
