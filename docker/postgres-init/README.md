# Docker PostgreSQL Setup for AI Integration

This directory contains initialization scripts for the PostgreSQL database with pgvector extension.

## What It Does

The `01-init-databases.sh` script:
1. Creates the `ipitch_interactions` database (in addition to the default `ipitch` database)
2. Enables the `pgvector` extension on both databases
3. Verifies the extension installation

## Databases Created

1. **ipitch** (default)
   - Used by: `proposal-manager`
   - Contains: proposals, users, contributors
   - Vector-enabled: ✅

2. **ipitch_interactions**
   - Used by: `interaction-manager`
   - Contains: comments, votes, inferred entities
   - Vector-enabled: ✅

## AI Integration Tables

Both databases will have the following AI-related tables created automatically by Hibernate:

### In `ipitch` database:
- `embeddings` - Vector embeddings for proposals
- `proposal_analysis` - AI analysis results for proposals

### In `ipitch_interactions` database:
- `embeddings` - Vector embeddings for comments
- `comment_analysis` - AI analysis results for comments

## How It Works

1. Docker Compose mounts this directory to `/docker-entrypoint-initdb.d` in the PostgreSQL container
2. On first startup, PostgreSQL executes all `.sh` and `.sql` files in alphabetical order
3. The script creates both databases and enables pgvector on each
4. Services can then connect to their respective databases with the same credentials

## Connection Details

All services use the same credentials:
- **Username**: `ipitch`
- **Password**: `ipitch123`
- **Host**: `localhost` (or `postgres` from within Docker network)
- **Port**: `5432`

### proposal-manager
```yaml
datasource:
  url: jdbc:postgresql://localhost:5432/ipitch
  username: ipitch
  password: ipitch123
```

### interaction-manager
```yaml
datasource:
  url: jdbc:postgresql://localhost:5432/ipitch_interactions
  username: ipitch
  password: ipitch123
```

## Verification

After starting Docker Compose, verify the setup:

```bash
# Check databases exist
docker exec ipitch-postgres psql -U ipitch -c "\l"

# Verify pgvector on ipitch database
docker exec ipitch-postgres psql -U ipitch -d ipitch -c "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';"

# Verify pgvector on ipitch_interactions database
docker exec ipitch-postgres psql -U ipitch -d ipitch_interactions -c "SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';"
```

Expected output:
```
  extname | extversion
----------+------------
 vector   | 0.5.1
```

## Troubleshooting

### "pgvector extension not found"
- Ensure you're using the `pgvector/pgvector:pg16` Docker image
- Check Docker Compose logs: `docker-compose logs postgres`

### "Database already exists" errors
- This is normal on subsequent startups - scripts only run once on first initialization
- To reinitialize, remove the volume: `docker-compose down -v && docker-compose up`

### Connection refused
- Ensure PostgreSQL container is healthy: `docker-compose ps`
- Check healthcheck: `docker inspect ipitch-postgres | grep Health -A 10`
