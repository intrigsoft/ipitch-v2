package com.intrigsoft.ipitch.proposalmanager.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "proposal.git")
data class GitProperties(
    var repositoryPath: String = "/tmp/proposal-git-repo",
    var mainBranch: String = "main"
)
