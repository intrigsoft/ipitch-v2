# PostgreSQL Vector Database Configuration

## Overview
The iPitch platform uses PostgreSQL with the pgvector extension for AI-powered features including vector embeddings, semantic search, and RAG (Retrieval Augmented Generation).

## Architecture

### Database Setup
- **Image**: `pgvector/pgvector:pg16` (PostgreSQL 16 with pgvector extension pre-installed)
- **Container**: `ipitch-postgres`
- **Credentials**: `ipitch / ipitch123` (unified across all services)

### Two Databases Strategy

#### 1. `ipitch` Database
- **Used by**: `proposal-manager`
- **Port**: 5432
- **Purpose**: Proposal management, user data, contributors
- **AI Tables**:
  - `embeddings` - Vector embeddings for proposals (3072 dimensions)
  - `proposal_analysis` - AI analysis results (summary, clarity, sector scores)

#### 2. `ipitch_interactions` Database
- **Used by**: `interaction-manager`
- **Port**: 5432 (same instance, different database)
- **Purpose**: Comments, votes, inferred entities
- **AI Tables**:
  - `embeddings` - Vector embeddings for comments (3072 dimensions)
  - `comment_analysis` - AI analysis results (governance, relevance, mode)

### Why Two Databases?

1. **Service Isolation**: Each microservice manages its own domain
2. **Independent Scaling**: Can split to separate instances later if needed
3. **Clear Boundaries**: Proposals vs Interactions are distinct domains
4. **AI Integration**: Both databases support vector operations independently

## Docker Compose Configuration

```yaml
postgres:
  image: pgvector/pgvector:pg16
  container_name: ipitch-postgres
  environment:
    POSTGRES_DB: ipitch          # Default database
    POSTGRES_USER: ipitch
    POSTGRES_PASSWORD: ipitch123
  ports:
    - "5432:5432"
  volumes:
    - postgres_data:/var/lib/postgresql/data
    # Initialization scripts
    - ./docker/postgres-init:/docker-entrypoint-initdb.d
  networks:
    - ipitch-network
```

## Initialization Process

### Automatic Setup on First Startup

The PostgreSQL container runs initialization scripts from `/docker-entrypoint-initdb.d` in alphabetical order:

1. **`01-init-databases.sh`**:
   - Creates `ipitch_interactions` database
   - Enables `pgvector` extension on `ipitch` (default)
   - Enables `pgvector` extension on `ipitch_interactions`
   - Verifies installation

2. **`V1__enable_pgvector.sql`** (from ai-integration):
   - Additional pgvector verification
   - Runs if migrations are mounted

3. **`V2__create_indexes.sql`** (from ai-integration):
   - Creates HNSW indexes for fast vector similarity search
   - Optimizes embedding queries

### Script: `docker/postgres-init/01-init-databases.sh`

```bash
#!/bin/bash
set -e

# Create ipitch_interactions database
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    SELECT 'CREATE DATABASE ipitch_interactions'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'ipitch_interactions')\gexec
EOSQL

# Enable pgvector on ipitch database
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname ipitch <<-EOSQL
    CREATE EXTENSION IF NOT EXISTS vector;
    SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
EOSQL

# Enable pgvector on ipitch_interactions database
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname ipitch_interactions <<-EOSQL
    CREATE EXTENSION IF NOT EXISTS vector;
    SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
EOSQL

echo "Database initialization complete!"
```

## Service Configurations

### proposal-manager

**application.yml**:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ipitch
    username: ipitch
    password: ipitch123
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: update  # Auto-create tables
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

# AI Integration
ai:
  provider: openai
  openai:
    api-key: ${OPENAI_API_KEY:}
    model: gpt-4-turbo-preview
    embedding-model: text-embedding-3-large

vector-db:
  embedding-dimension: 3072
  similarity-function: cosine
```

**Tables Created**:
- `proposals`
- `contributors`
- `users`
- `embeddings` (AI)
- `proposal_analysis` (AI)

### interaction-manager

**application.yml**:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ipitch_interactions
    username: ipitch
    password: ipitch123
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

# AI Integration
ai:
  provider: openai
  openai:
    api-key: ${OPENAI_API_KEY:}
    model: gpt-4-turbo-preview
    embedding-model: text-embedding-3-large

vector-db:
  embedding-dimension: 3072
  similarity-function: cosine
```

**Tables Created**:
- `comments`
- `votes`
- `inferred_entities`
- `embeddings` (AI)
- `comment_analysis` (AI)

### ai-integration (Library Module)

**application.yml**:
```yaml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/ipitch}
    username: ${DATABASE_USER:ipitch}
    password: ${DATABASE_PASSWORD:ipitch123}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: update

  elasticsearch:
    uris: ${ELASTICSEARCH_URIS:http://localhost:9200}
```

**Purpose**: Configuration for standalone testing and development

## Vector Database Tables

### `embeddings` Table

**Purpose**: Store vector embeddings for semantic search

```sql
CREATE TABLE embeddings (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,  -- 'PROPOSAL' or 'COMMENT'
    entity_id UUID NOT NULL,
    embedding vector(3072) NOT NULL,   -- pgvector type
    model VARCHAR(100) NOT NULL,
    dimension INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,

    INDEX idx_embeddings_entity (entity_type, entity_id)
);

-- HNSW index for fast similarity search
CREATE INDEX idx_embeddings_vector
ON embeddings
USING hnsw (embedding vector_cosine_ops);
```

**Query Examples**:
```sql
-- Find similar proposals using cosine distance
SELECT * FROM embeddings
WHERE entity_type = 'PROPOSAL'
ORDER BY embedding <=> CAST('[0.1, 0.2, ...]' AS vector)
LIMIT 5;

-- Find proposals within similarity threshold
SELECT e.*, (e.embedding <=> CAST('[...]' AS vector)) as distance
FROM embeddings e
WHERE entity_type = 'PROPOSAL'
  AND (e.embedding <=> CAST('[...]' AS vector)) <= 0.7
ORDER BY distance
LIMIT 10;
```

### `proposal_analysis` Table

**Purpose**: Store AI analysis results for proposals

```sql
CREATE TABLE proposal_analysis (
    id UUID PRIMARY KEY,
    proposal_id UUID UNIQUE NOT NULL,
    summary TEXT NOT NULL,
    clarity_score DOUBLE PRECISION NOT NULL,
    sector_scores JSONB NOT NULL,  -- [{"sector": "IT", "score": 8.5}, ...]
    embedding_id UUID,
    model VARCHAR(100) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    analyzed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    INDEX idx_proposal_analysis_proposal_id (proposal_id),
    INDEX idx_proposal_analysis_recent (analyzed_at DESC)
        WHERE analyzed_at > NOW() - INTERVAL '30 days'
);
```

### `comment_analysis` Table

**Purpose**: Store AI analysis results for comments

```sql
CREATE TABLE comment_analysis (
    id UUID PRIMARY KEY,
    comment_id UUID UNIQUE NOT NULL,
    governance_flags JSONB NOT NULL,  -- ["NONE"] or ["HATE_SPEECH", "VIOLENCE"]
    governance_score DOUBLE PRECISION NOT NULL,
    is_flagged BOOLEAN NOT NULL,
    flag_reason TEXT,
    relevance_score DOUBLE PRECISION,
    sector_scores JSONB,
    mode VARCHAR(50),  -- SUPPORTIVE, CRITICAL, NEUTRAL, INQUISITIVE, SUGGESTIVE
    is_marketing BOOLEAN NOT NULL,
    marketing_score DOUBLE PRECISION,
    model VARCHAR(100) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    analyzed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    INDEX idx_comment_analysis_comment_id (comment_id),
    INDEX idx_comment_analysis_flagged (is_flagged) WHERE is_flagged = true,
    INDEX idx_comment_analysis_marketing (is_marketing) WHERE is_marketing = true
);
```

## Environment Variables

### Required
```bash
# OpenAI API Key (for AI analysis)
export OPENAI_API_KEY="sk-..."
```

### Optional
```bash
# Database (defaults are fine for local development)
export DATABASE_URL="jdbc:postgresql://localhost:5432/ipitch"
export DATABASE_USER="ipitch"
export DATABASE_PASSWORD="ipitch123"

# Elasticsearch
export ELASTICSEARCH_URIS="http://localhost:9200"

# Alternative AI Providers
export ANTHROPIC_API_KEY="sk-ant-..."
export LOCAL_AI_URL="http://localhost:11434"
```

## Setup Instructions

### 1. Start Services
```bash
# Start PostgreSQL, Elasticsearch, and initialize databases
docker-compose up -d

# Wait for initialization (check logs)
docker-compose logs -f postgres
```

Expected logs:
```
ipitch-postgres | Creating databases and enabling pgvector extension...
ipitch-postgres | Databases created successfully
ipitch-postgres | pgvector extension enabled on ipitch database
ipitch-postgres | pgvector extension enabled on ipitch_interactions database
ipitch-postgres | Database initialization complete!
```

### 2. Verify Setup
```bash
# List databases
docker exec ipitch-postgres psql -U ipitch -c "\l"

# Check pgvector on ipitch
docker exec ipitch-postgres psql -U ipitch -d ipitch \
  -c "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';"

# Check pgvector on ipitch_interactions
docker exec ipitch-postgres psql -U ipitch -d ipitch_interactions \
  -c "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';"

# Verify tables (after running services)
docker exec ipitch-postgres psql -U ipitch -d ipitch -c "\dt"
docker exec ipitch-postgres psql -U ipitch -d ipitch_interactions -c "\dt"
```

### 3. Set API Key
```bash
export OPENAI_API_KEY="your-api-key-here"
```

### 4. Start Application Services
```bash
# Build all modules
./gradlew build

# Start proposal-manager (port 8081)
./gradlew :proposal-manager:bootRun

# Start interaction-manager (port 8083) in another terminal
./gradlew :interaction-manager:bootRun
```

## Verification Queries

### Check Vector Embeddings
```sql
-- Connect to ipitch database
\c ipitch

-- View proposal embeddings
SELECT
    id,
    entity_type,
    entity_id,
    model,
    dimension,
    created_at
FROM embeddings
WHERE entity_type = 'PROPOSAL'
LIMIT 10;

-- View proposal analysis results
SELECT
    proposal_id,
    LEFT(summary, 100) as summary,
    clarity_score,
    sector_scores,
    provider,
    analyzed_at
FROM proposal_analysis
LIMIT 10;
```

### Check Comment Analysis
```sql
-- Connect to ipitch_interactions database
\c ipitch_interactions

-- View flagged comments
SELECT
    comment_id,
    governance_flags,
    governance_score,
    flag_reason,
    analyzed_at
FROM comment_analysis
WHERE is_flagged = true
LIMIT 10;

-- View comment mode distribution
SELECT
    mode,
    COUNT(*) as count
FROM comment_analysis
WHERE mode IS NOT NULL
GROUP BY mode
ORDER BY count DESC;
```

## Troubleshooting

### Issue: "pgvector extension not found"

**Cause**: Wrong PostgreSQL image or extension not installed

**Solution**:
```bash
# Ensure using pgvector image
docker-compose down -v
docker-compose up -d

# Verify image
docker inspect ipitch-postgres | grep Image
# Should show: "pgvector/pgvector:pg16"
```

### Issue: "Database already exists" errors on startup

**Cause**: Normal behavior - initialization scripts only run once

**Solution**: Ignore these errors, or to reinitialize:
```bash
docker-compose down -v  # WARNING: Deletes all data
docker-compose up -d
```

### Issue: Connection refused from services

**Cause**: PostgreSQL not ready or wrong connection string

**Solution**:
```bash
# Check PostgreSQL is healthy
docker-compose ps

# Check logs
docker-compose logs postgres

# Test connection
docker exec ipitch-postgres pg_isready -U ipitch
```

### Issue: Tables not created

**Cause**: Hibernate not running or wrong ddl-auto setting

**Solution**:
- Ensure `spring.jpa.hibernate.ddl-auto: update` in application.yml
- Check service logs for Hibernate errors
- Manually verify with: `docker exec ipitch-postgres psql -U ipitch -d ipitch -c "\dt"`

### Issue: Vector similarity queries return no results

**Cause**: HNSW index not created or embeddings not stored

**Solution**:
```sql
-- Check if HNSW index exists
SELECT indexname FROM pg_indexes
WHERE tablename = 'embeddings';

-- Manually create if missing
CREATE INDEX idx_embeddings_vector
ON embeddings
USING hnsw (embedding vector_cosine_ops);

-- Check if embeddings exist
SELECT COUNT(*) FROM embeddings;
```

## Performance Tuning

### HNSW Index Parameters
```sql
-- For better recall but slower build
CREATE INDEX idx_embeddings_vector
ON embeddings
USING hnsw (embedding vector_cosine_ops)
WITH (m = 32, ef_construction = 128);

-- For faster build but lower recall
CREATE INDEX idx_embeddings_vector
ON embeddings
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);
```

### Connection Pool Settings

For production, update application.yml:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

## Backup & Restore

### Backup
```bash
# Backup both databases
docker exec ipitch-postgres pg_dump -U ipitch ipitch > ipitch_backup.sql
docker exec ipitch-postgres pg_dump -U ipitch ipitch_interactions > ipitch_interactions_backup.sql
```

### Restore
```bash
# Restore ipitch database
docker exec -i ipitch-postgres psql -U ipitch -d ipitch < ipitch_backup.sql

# Restore ipitch_interactions database
docker exec -i ipitch-postgres psql -U ipitch -d ipitch_interactions < ipitch_interactions_backup.sql
```

## Migration to Production

For production deployment:

1. **Use managed PostgreSQL** with pgvector support:
   - AWS RDS doesn't support pgvector yet (use EC2 or Supabase)
   - Google Cloud SQL supports pgvector (PostgreSQL 14+)
   - Azure Database for PostgreSQL supports pgvector
   - Supabase (built on PostgreSQL with pgvector included)

2. **Update connection strings**:
```yaml
spring:
  datasource:
    url: ${DATABASE_URL}  # From environment
    username: ${DATABASE_USER}
    password: ${DATABASE_PASSWORD}
```

3. **Use SSL**:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://prod-host:5432/ipitch?ssl=true&sslmode=require
```

4. **Disable auto-ddl**:
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # Don't auto-create in production
```

5. **Use migration tools**:
   - Flyway or Liquibase for schema versioning
   - Manual index creation for performance tuning

## Summary

✅ **Two PostgreSQL databases** (`ipitch`, `ipitch_interactions`) in one instance
✅ **pgvector extension** enabled on both for AI features
✅ **Automatic initialization** via Docker Compose
✅ **Unified credentials** (ipitch/ipitch123) across all services
✅ **AI-ready** with vector embeddings and analysis tables
✅ **Fully configured** for proposal-manager and interaction-manager
✅ **Production-ready** architecture with clear separation of concerns

All AI integration features (vector search, semantic similarity, RAG, user analytics) are now fully supported by the database infrastructure!
