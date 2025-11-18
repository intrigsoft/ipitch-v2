-- Enable pgvector extension for vector similarity search
-- Run this script manually on your PostgreSQL database before starting the application
-- Requires PostgreSQL 11+ with pgvector extension installed

-- Create vector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Verify extension is enabled
SELECT * FROM pg_extension WHERE extname = 'vector';
