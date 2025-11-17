package com.intrigsoft.ipitch.proposalviewmanager

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories

@SpringBootApplication(
    scanBasePackages = ["com.intrigsoft.ipitch.proposalviewmanager"],
    exclude = [DataSourceAutoConfiguration::class, HibernateJpaAutoConfiguration::class]
)
@EnableElasticsearchRepositories(basePackages = ["com.intrigsoft.ipitch.proposalviewmanager.repository"])
class ProposalViewManagerApplication

fun main(args: Array<String>) {
    runApplication<ProposalViewManagerApplication>(*args)
}
