# Proposal Manager

A Git-based proposal management system that manages proposal metadata in PostgreSQL and proposal content in a local Git repository.

## Overview

The Proposal Manager provides a comprehensive workflow for creating, managing, and publishing proposals using a Git-based version control system. Each proposal has its own working branch, and contributors work on their own branches, creating pull requests to merge their changes.

## Architecture

### Components

1. **Database (PostgreSQL)**: Stores proposal metadata, contributors, and users
2. **Git Repository**: Stores proposal content and title with full version history
3. **Spring Boot Application**: REST API layer with business logic

### Key Entities

#### Proposal
- `id`: UUID
- `title`: String
- `content`: String (also stored in Git)
- `ownerId`: UUID (references User)
- `contributors`: List of contributors
- `version`: Semantic version (e.g., "0.0.1")
- `status`: DRAFT | PUBLISHED | CONCLUDED | EXPIRED
- `stats`: JSON field for statistics
- `workingBranch`: Git branch name
- `gitCommitHash`: Latest commit hash

#### Contributor
- `id`: UUID
- `userId`: UUID
- `role`: String (e.g., "EDITOR", "REVIEWER")
- `status`: PENDING | ACTIVE | SUSPENDED

#### User
- `userId`: UUID
- `userName`: String
- `email`: String

## Git Workflow

### Branch Structure

```
main
├── proposal/{proposalId}                          # Working branch for each proposal
│   ├── contributor/{contributorId}                # Contributor's personal branch
│   └── contributor/{anotherContributorId}
└── ...
```

### Workflow Steps

1. **Create Proposal**: Creates a proposal directory in Git with `metadata.json` and `content.md` files
2. **Add Contributor**: Creates a contributor branch from the proposal's working branch
3. **Update Content/Title**: Commits changes to the contributor's branch
4. **Create Pull Request**: Simulates a PR from contributor branch to proposal working branch
5. **Merge Pull Request**: Owner merges contributor changes into the proposal working branch
6. **Publish**: Merges the proposal working branch to main and tags with version (e.g., `{proposalId}-0.0.1`)

## API Endpoints

### Base URL
```
http://localhost:8081/api/proposals
```

### Swagger Documentation
```
http://localhost:8081/swagger-ui.html
```

### Operations

#### 1. Create Proposal
```http
POST /api/proposals
Content-Type: application/json

{
  "title": "Sample Proposal",
  "content": "# Proposal Content\n\nThis is the proposal content.",
  "ownerId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response**: ProposalResponse with created proposal details

**Git Actions**:
- Creates directory `{proposalId}/` with `metadata.json` and `content.md`
- Commits to main branch
- Creates working branch `proposal/{proposalId}`

---

#### 2. Update Proposal Metadata
```http
PUT /api/proposals/{proposalId}/metadata
Content-Type: application/json

{
  "status": "PUBLISHED",
  "stats": {
    "views": 100,
    "likes": 25
  }
}
```

**Response**: Updated ProposalResponse

**Git Actions**: None (metadata only)

---

#### 3. Add Contributor
```http
POST /api/proposals/{proposalId}/contributors
Content-Type: application/json

{
  "userId": "660e8400-e29b-41d4-a716-446655440001",
  "role": "EDITOR"
}
```

**Response**: ContributorResponse

**Git Actions**:
- Creates branch `proposal/{proposalId}/contributor/{contributorId}` from working branch

---

#### 4. Remove Contributor
```http
DELETE /api/proposals/{proposalId}/contributors/{contributorId}
```

**Response**: Success message

**Git Actions**: None (branch remains in Git)

---

#### 5. Update Content
```http
PUT /api/proposals/{proposalId}/content
Content-Type: application/json

{
  "content": "# Updated Content\n\nThis is the updated proposal content.",
  "contributorId": "770e8400-e29b-41d4-a716-446655440002",
  "commitMessage": "Updated proposal content with new sections"
}
```

**Response**: Updated ProposalResponse

**Git Actions**:
- Checks out contributor branch
- Updates `content.md`
- Commits with provided message

---

#### 6. Update Title
```http
PUT /api/proposals/{proposalId}/title
Content-Type: application/json

{
  "title": "Updated Proposal Title",
  "contributorId": "770e8400-e29b-41d4-a716-446655440002",
  "commitMessage": "Updated proposal title"
}
```

**Response**: Updated ProposalResponse

**Git Actions**:
- Checks out contributor branch
- Updates `metadata.json`
- Commits with provided message

---

#### 7. Create Pull Request
```http
POST /api/proposals/{proposalId}/pull-requests
Content-Type: application/json

{
  "contributorId": "770e8400-e29b-41d4-a716-446655440002",
  "description": "Added new sections and updated formatting"
}
```

**Response**: PullRequestResponse with PR ID

**Git Actions**:
- Creates PR metadata file in `.pull-requests/{prId}.json`

---

#### 8. Merge Pull Request
```http
POST /api/proposals/{proposalId}/pull-requests/merge?ownerId={ownerId}
Content-Type: application/json

{
  "pullRequestId": "pr-uuid-here",
  "commitMessage": "Merge contributor updates"
}
```

**Response**: Commit hash

**Git Actions**:
- Merges contributor branch into proposal working branch
- Updates PR status to "MERGED"

**Authorization**: Only proposal owner can merge

---

#### 9. Publish Proposal
```http
POST /api/proposals/{proposalId}/publish?ownerId={ownerId}
```

**Response**: Updated ProposalResponse with new version

**Git Actions**:
- Merges proposal working branch to main
- Creates version tag: `{proposalId}-{version}` (e.g., `550e8400-...-0.0.1`)
- Increments version in database

**Authorization**: Only proposal owner can publish

---

#### 10. Get Proposal
```http
GET /api/proposals/{proposalId}
```

**Response**: ProposalResponse

---

#### 11. Get All Proposals
```http
GET /api/proposals
```

**Response**: List of ProposalResponse

---

## Configuration

### Application Properties (`application.yml`)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ipitch_proposals
    username: postgres
    password: postgres

proposal:
  git:
    repository-path: /tmp/proposal-git-repo  # Can be overridden with PROPOSAL_GIT_REPO env var
    main-branch: main

server:
  port: 8081
```

### Environment Variables

- `PROPOSAL_GIT_REPO`: Path to Git repository (default: `/tmp/proposal-git-repo`)

## Setup & Running

### Prerequisites

1. PostgreSQL 14+ running on `localhost:5432`
2. Database named `ipitch_proposals`
3. JDK 17+

### Database Setup

```sql
CREATE DATABASE ipitch_proposals;
```

The application will auto-create tables using Hibernate DDL.

### Build & Run

```bash
# Build
./gradlew :proposal-manager:build

# Run
./gradlew :proposal-manager:bootRun
```

## Logging

The application uses structured logging with the following key log points:

### API Layer
- Request received with parameters
- Operation completed with result

### Service Layer
- Business operation started
- Database operations
- Git operations triggered
- Operation completion or failure

### Git Service Layer
- Repository initialization
- Branch operations (create, checkout)
- Commit operations
- Merge operations
- Tag creation

### Log Levels

- `INFO`: Key operations and flow tracking
- `DEBUG`: Detailed operation info (file paths, branch names)
- `WARN`: Non-critical issues (existing branches, contributor conflicts)
- `ERROR`: Failures and exceptions

### Example Log Output

```
INFO  - API: Creating proposal with title: Sample Proposal
INFO  - Creating new proposal: Sample Proposal for owner 550e8400-e29b-41d4-a716-446655440000
INFO  - Proposal created in database with ID: 770e8400-e29b-41d4-a716-446655440003
INFO  - Creating proposal 770e8400-e29b-41d4-a716-446655440003 in Git repository
DEBUG - Created proposal directory: /tmp/proposal-git-repo/770e8400-e29b-41d4-a716-446655440003
DEBUG - Created proposal files in /tmp/proposal-git-repo/770e8400-e29b-41d4-a716-446655440003
INFO  - Created initial commit for proposal 770e8400-e29b-41d4-a716-446655440003: a1b2c3d4
INFO  - Created working branch: proposal/770e8400-e29b-41d4-a716-446655440003
INFO  - Proposal 770e8400-e29b-41d4-a716-446655440003 created successfully
INFO  - API: Proposal created successfully with ID: 770e8400-e29b-41d4-a716-446655440003
```

## Error Handling

All exceptions are handled by `GlobalExceptionHandler` and return structured error responses:

```json
{
  "success": false,
  "message": "Error description",
  "data": null
}
```

### Common HTTP Status Codes

- `200 OK`: Successful operation
- `201 Created`: Resource created
- `400 Bad Request`: Validation error or invalid operation
- `403 Forbidden`: Unauthorized operation
- `404 Not Found`: Resource not found
- `500 Internal Server Error`: Unexpected error

## Testing

Access Swagger UI at `http://localhost:8081/swagger-ui.html` to test all endpoints interactively.

## Project Structure

```
proposal-manager/
├── src/main/kotlin/com/example/ipitch/proposalmanager/
│   ├── api/
│   │   └── ProposalController.kt          # REST endpoints
│   ├── config/
│   │   └── GitProperties.kt               # Configuration properties
│   ├── domain/
│   │   ├── Proposal.kt                    # Proposal entity
│   │   ├── Contributor.kt                 # Contributor entity
│   │   ├── User.kt                        # User entity
│   │   ├── ProposalStatus.kt              # Status enum
│   │   └── ContributorStatus.kt           # Contributor status enum
│   ├── dto/
│   │   ├── request/                       # Request DTOs
│   │   └── response/                      # Response DTOs
│   ├── exception/
│   │   ├── Exceptions.kt                  # Custom exceptions
│   │   └── GlobalExceptionHandler.kt      # Exception handler
│   ├── repository/
│   │   ├── ProposalRepository.kt          # JPA repository
│   │   ├── ContributorRepository.kt
│   │   └── UserRepository.kt
│   ├── service/
│   │   ├── ProposalService.kt             # Business logic
│   │   └── GitService.kt                  # Git operations
│   └── ProposalManagerApplication.kt      # Main application
└── src/main/resources/
    └── application.yml                    # Configuration
```

## Future Enhancements

1. Integration with GitHub/GitLab for real pull requests
2. Conflict detection and resolution UI
3. User authentication and authorization
4. Email notifications for PR events
5. Advanced search and filtering
6. Proposal templates
7. Review and approval workflows
8. Activity timeline
9. File attachments support
10. Real-time collaboration features
