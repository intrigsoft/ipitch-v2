plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    `java-library`
}

dependencies {
    // Common module dependency
    api(project(":common"))

    // Spring Boot - exclude default logging
    api("org.springframework.boot:spring-boot-starter:3.3.5") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:3.3.5") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch:3.3.5") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }

    // Log4j2 for logging
    api("org.springframework.boot:spring-boot-starter-log4j2:3.3.5")

    // Kotlin
    api("org.jetbrains.kotlin:kotlin-reflect")
    api("com.fasterxml.jackson.module:jackson-module-kotlin")

    // PostgreSQL with vector support
    api("org.postgresql:postgresql")

    // HTTP Client for AI APIs
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.3.5")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    // Explicit version because this module doesn't apply Spring Boot's dependency management
    testImplementation("com.h2database:h2:2.2.224")
}
