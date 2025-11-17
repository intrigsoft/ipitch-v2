package com.intrigsoft.ipitch.proposalmanager

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@ConfigurationPropertiesScan
@EntityScan("com.intrigsoft.ipitch.domain")
@EnableJpaRepositories("com.intrigsoft.ipitch.repository")
class ProposalManagerApplication

fun main(args: Array<String>) {
    runApplication<ProposalManagerApplication>(*args)
}
