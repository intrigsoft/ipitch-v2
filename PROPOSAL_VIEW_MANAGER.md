# Proposal View Manager Implementation

This document describes the implementation of the Proposal View Manager service with Elasticsearch integration.

## Overview

The Proposal View Manager is a microservice that provides search and view capabilities for proposals using Elasticsearch. It works in conjunction with the Proposal Manager service to provide a comprehensive proposal management system.

## Architecture

### Components

1. **Proposal Manager** (Port 8081)
   - Manages proposal CRUD operations with PostgreSQL
   - Uses Git for version control
   - Publishes proposals to View Manager via Feign Client

2. **Proposal View Manager** (Port 8082)
   - Indexes proposals in Elasticsearch
   - Provides search and view APIs
   - Internal API for proposal publishing
   - Public API for frontend consumption

3. **Infrastructure Services**
   - PostgreSQL (Port 5432) - Database for Proposal Manager
   - Elasticsearch (Port 9200) - Search engine for View Manager

## Features

### Internal API (for Proposal Manager)

#### POST `/internal/api/v1/proposals/publish`
Publishes a proposal to be indexed in Elasticsearch.

**Request Body:**
```json
{
  "id": "uuid",
  "title": "string",
  "content": "string",
  "ownerId": "uuid",
  "ownerName": "string",
  "contributors": [
    {
      "id": "uuid",
      "userId": "uuid",
      "userName": "string",
      "role": "string",
      "status": "string"
    }
  ],
  "version": "string",
  "status": "DRAFT|PUBLISHED",
  "stats": {},
  "workingBranch": "string",
  "gitCommitHash": "string",
  "createdAt": "datetime",
  "updatedAt": "datetime"
}
```

#### DELETE `/internal/api/v1/proposals/{proposalId}`
Removes a proposal from the Elasticsearch index.

### Public API (for Frontend)

#### GET `/api/v1/proposals/search`
Search proposals with advanced filtering.

**Query Parameters:**
- `query` - Search text (searches in title and content)
- `ownerId` - Filter by owner UUID
- `status` - Filter by status (DRAFT, PUBLISHED)
- `fromDate` - Filter by start date (ISO format)
- `toDate` - Filter by end date (ISO format)
- `page` - Page number (0-indexed, default: 0)
- `size` - Page size (default: 20)
- `sortBy` - Sort field (default: updatedAt)
- `sortOrder` - Sort order (asc/desc, default: desc)

**Response:**
```json
{
  "content": [...],
  "totalElements": 100,
  "totalPages": 5,
  "page": 0,
  "size": 20
}
```

#### GET `/api/v1/proposals/{proposalId}`
Get a single proposal by ID.

#### GET `/api/v1/proposals`
Get all proposals with pagination.

**Query Parameters:**
- `page` - Page number (0-indexed, default: 0)
- `size` - Page size (default: 20)
- `sortBy` - Sort field (default: updatedAt)
- `sortOrder` - Sort order (asc/desc, default: desc)

## Integration Flow

### Publishing a Proposal

When a proposal is published in Proposal Manager:

1. Proposal Manager updates the proposal status to PUBLISHED in PostgreSQL
2. Proposal Manager calls View Manager's internal API via Feign Client
3. View Manager indexes the proposal in Elasticsearch (replaces any existing version)
4. Frontend can now search and view the proposal via public API

**Note**: Only one version of each proposal exists in Elasticsearch at any time. When a new version is published, it replaces the previous version in the index using the same document ID (proposal UUID).

### Reverting a Proposal

When a proposal is reverted to a previous version:

1. Owner initiates revert via `POST /api/proposals/{proposalId}/revert`
2. Proposal Manager reverts Git repository to previous version tag
3. Database is updated with previous version data
4. Two scenarios:
   - **First version revert**: Proposal is marked as DRAFT and removed from Elasticsearch
   - **Other versions**: Previous version is re-indexed in Elasticsearch, replacing the current version
5. Current version tag is deleted from Git repository

## Running the Services

### 1. Start Infrastructure Services

```bash
docker-compose up -d
```

This starts:
- PostgreSQL on port 5432
- Elasticsearch on port 9200

### 2. Start Proposal Manager

```bash
./gradlew :proposal-manager:bootRun
```

Access Swagger UI: http://localhost:8081/swagger-ui.html

### 3. Start Proposal View Manager

```bash
./gradlew :proposal-view-manager:bootRun
```

Access Swagger UI: http://localhost:8082/swagger-ui.html

## Configuration

### Proposal Manager (`proposal-manager/src/main/resources/application.yml`)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ipitch
    username: ipitch
    password: ipitch123

proposal-view-manager:
  url: http://localhost:8082

feign:
  client:
    config:
      default:
        connectTimeout: 5000
        readTimeout: 5000
```

### Proposal View Manager (`proposal-view-manager/src/main/resources/application.yml`)

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200
```

## Environment Variables

- `ELASTICSEARCH_URL` - Elasticsearch URL (default: http://localhost:9200)
- `PROPOSAL_VIEW_MANAGER_URL` - View Manager URL (default: http://localhost:8082)
- `PROPOSAL_GIT_REPO` - Git repository path (default: /tmp/proposal-git-repo)

## API Documentation

Both services provide OpenAPI/Swagger documentation:

- Proposal Manager: http://localhost:8081/swagger-ui.html
- Proposal View Manager: http://localhost:8082/swagger-ui.html

## Data Flow

```
┌─────────────────────┐
│   Proposal Manager  │
│     (Port 8081)     │
│                     │
│   - PostgreSQL DB   │
│   - Git VCS         │
│   - CRUD Operations │
└──────────┬──────────┘
           │ Feign Client
           │ POST /internal/api/v1/proposals/publish
           ▼
┌─────────────────────┐
│ Proposal View Mgr   │
│    (Port 8082)      │
│                     │
│  - Elasticsearch    │
│  - Search/View API  │
└──────────┬──────────┘
           │
           │ GET /api/v1/proposals/search
           ▼
┌─────────────────────┐
│      Frontend       │
└─────────────────────┘
```

## Development Notes

### Key Technologies

- **Spring Boot 3.3.5** - Application framework
- **Kotlin 1.9.25** - Programming language
- **Spring Data Elasticsearch** - Elasticsearch integration
- **Spring Cloud OpenFeign** - HTTP client for inter-service communication
- **PostgreSQL 16** - Relational database
- **Elasticsearch 8.11.0** - Search engine

### Module Structure

```
ipitch-v2/
├── common/                          # Shared domain entities and repositories
├── proposal-manager/                # Proposal management service
│   ├── client/                      # Feign clients
│   ├── service/                     # Business logic
│   ├── api/                         # REST controllers
│   └── dto/                         # Data transfer objects
└── proposal-view-manager/           # Proposal search and view service
    ├── document/                    # Elasticsearch documents
    ├── repository/                  # Elasticsearch repositories
    ├── service/                     # Business logic
    ├── api/                         # REST controllers
    └── dto/                         # Data transfer objects
```

## Error Handling

The Proposal Manager is designed to be resilient:
- If View Manager is unavailable during publishing, the proposal is still saved to PostgreSQL
- Errors are logged but don't fail the publish operation
- Feign client has configurable timeouts (5 seconds default)

## Future Enhancements

- Add authentication and authorization
- Implement real-time indexing with message queues (Kafka/RabbitMQ)
- Add full-text search with advanced Elasticsearch features
- Implement search result highlighting
- Add search analytics and metrics
- Support for proposal versioning in search results
- Faceted search capabilities

## Proposal Manager API

### Revert Endpoint

#### POST `/api/proposals/{proposalId}/revert`

Reverts a published proposal to its previous version.

**Query Parameters:**
- `ownerId` - UUID of the proposal owner (required for authorization)

**Behavior:**
- **First version (0.0.1)**: 
  - Proposal status changed to DRAFT
  - Removed from Elasticsearch index
  - Git repository reverted to pre-publish state
  
- **Subsequent versions (e.g., 0.0.2 → 0.0.1)**:
  - Proposal data reverted to previous version
  - Previous version re-indexed in Elasticsearch
  - Current version tag deleted from Git

**Response:**
```json
{
  "success": true,
  "message": "Proposal reverted successfully to version 0.0.1",
  "data": {
    "id": "uuid",
    "title": "Previous Title",
    "content": "Previous Content",
    "version": "0.0.1",
    "status": "PUBLISHED",
    ...
  }
}
```

**Error Cases:**
- Not the owner: 403 Unauthorized
- Proposal not PUBLISHED: 400 Bad Request
- Git revert fails: 500 Internal Server Error

## Version Management

### How Versioning Works

1. **Version Format**: Semantic versioning (major.minor.patch)
2. **Initial Version**: 0.0.0 (incremented to 0.0.1 on first publish)
3. **Increment Strategy**: Patch version incremented on each publish
4. **Git Tags**: Each published version creates a tag: `{proposalId}-{version}`

### Elasticsearch Version Guarantee

The implementation ensures only one version of each proposal exists in Elasticsearch:

- **Document ID**: Proposal UUID (consistent across versions)
- **Indexing Strategy**: Elasticsearch `save()` operation with same ID updates existing document
- **Revert Behavior**: Previous version replaces current in index
- **Delete on First Revert**: Unpublished proposals removed from index

This design prevents version proliferation in search results while maintaining full Git history.
