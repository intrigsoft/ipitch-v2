# ipitch-v2

Multi-module Kotlin Spring Boot project (Gradle Kotlin DSL) with the following modules:

- common (shared Kotlin library)
- proposal-manager (runnable Spring Boot app)
- proposal-view-manager (runnable Spring Boot app)
- interaction-manager (runnable Spring Boot app)

The `common` module is a dependency of all runnable modules.

Project requires:
- JDK 17+
- Gradle 8+ (or use Gradle wrapper if you add it)

Structure
- settings.gradle.kts — declares modules
- build.gradle.kts — centralizes plugin versions and repositories, sets JVM toolchain 17
- common — plain Kotlin library (no Spring)
- proposal-manager — Spring Boot web app on port 8081
- proposal-view-manager — Spring Boot web app on port 8082
- interaction-manager — Spring Boot web app on port 8083

How to build
- Build all modules:
  - Using local Gradle: `gradle build`
- Build a single module:
  - `gradle :proposal-manager:build`

How to run apps
- Proposal Manager:
  - `gradle :proposal-manager:bootRun`
  - Then open: http://localhost:8081/api/proposals/hello?name=Alice
- Proposal View Manager:
  - `gradle :proposal-view-manager:bootRun`
  - Then open: http://localhost:8082/api/views/hello?name=Bob
- Interaction Manager:
  - `gradle :interaction-manager:bootRun`
  - Then open: http://localhost:8083/api/interactions/hello?name=Carol

Notes
- Shared example service lives in `common/src/main/kotlin/com/example/ipitch/common/GreetingService.kt`.
- Update package names, ports, and dependencies as needed.