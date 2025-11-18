package com.intrigsoft.ipitch.usermanager

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@EntityScan("com.intrigsoft.ipitch.domain")
@EnableJpaRepositories("com.intrigsoft.ipitch.repository")
class UserManagerApplication

fun main(args: Array<String>) {
    runApplication<UserManagerApplication>(*args)
}
