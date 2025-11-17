package com.intrigsoft.ipitch.aiintegration.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Configuration properties for vector database
 */
@Configuration
@ConfigurationProperties(prefix = "vector-db")
data class VectorDatabaseProperties(
    var embeddingDimension: Int = 3072,  // text-embedding-3-large
    var similarityFunction: String = "cosine"  // cosine, l2, inner_product
)
