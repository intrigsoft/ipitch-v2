plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    `java-library`
}

dependencies {
    // Spring Data JPA
    api("org.springframework.boot:spring-boot-starter-data-jpa:3.3.5")
    api("org.springframework.boot:spring-boot-starter-validation:3.3.5")

    // Spring Security OAuth2
    api("org.springframework.boot:spring-boot-starter-security:3.3.5")
    api("org.springframework.boot:spring-boot-starter-oauth2-resource-server:3.3.5")

    // Kotlin
    api("org.jetbrains.kotlin:kotlin-reflect")
    api("com.fasterxml.jackson.module:jackson-module-kotlin")

    // PostgreSQL
    api("org.postgresql:postgresql")
}
