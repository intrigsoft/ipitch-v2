#!/bin/bash
set -e

# This script creates multiple databases and enables pgvector extension on each
# It runs on first container startup

echo "Creating databases and enabling pgvector extension..."

# Create ipitch_interactions database
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    -- Create ipitch_interactions database if it doesn't exist
    SELECT 'CREATE DATABASE ipitch_interactions'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'ipitch_interactions')\gexec
EOSQL

echo "Databases created successfully"

# Enable pgvector extension on ipitch database (default)
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname ipitch <<-EOSQL
    -- Enable pgvector extension
    CREATE EXTENSION IF NOT EXISTS vector;

    -- Verify extension
    SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
EOSQL

echo "pgvector extension enabled on ipitch database"

# Enable pgvector extension on ipitch_interactions database
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname ipitch_interactions <<-EOSQL
    -- Enable pgvector extension
    CREATE EXTENSION IF NOT EXISTS vector;

    -- Verify extension
    SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
EOSQL

echo "pgvector extension enabled on ipitch_interactions database"

echo "Database initialization complete!"
