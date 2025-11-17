-- Create indexes for AI integration tables
-- These will be created automatically by Hibernate, but we can optimize them here

-- Index for embeddings table (vector similarity search)
-- Using HNSW (Hierarchical Navigable Small World) index for fast approximate nearest neighbor search
-- This index is created only if the table exists (will be created by Hibernate first)

DO $$
BEGIN
    -- Create HNSW index on embeddings if table exists
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'embeddings') THEN
        IF NOT EXISTS (SELECT FROM pg_indexes WHERE indexname = 'idx_embeddings_vector') THEN
            CREATE INDEX idx_embeddings_vector ON embeddings USING hnsw (embedding vector_cosine_ops);
        END IF;

        -- Index for entity lookups
        IF NOT EXISTS (SELECT FROM pg_indexes WHERE indexname = 'idx_embeddings_entity') THEN
            CREATE INDEX idx_embeddings_entity ON embeddings(entity_type, entity_id);
        END IF;
    END IF;

    -- Index for proposal analysis lookups
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'proposal_analysis') THEN
        IF NOT EXISTS (SELECT FROM pg_indexes WHERE indexname = 'idx_proposal_analysis_proposal_id') THEN
            CREATE INDEX idx_proposal_analysis_proposal_id ON proposal_analysis(proposal_id);
        END IF;

        -- Partial index for recent analyses
        IF NOT EXISTS (SELECT FROM pg_indexes WHERE indexname = 'idx_proposal_analysis_recent') THEN
            CREATE INDEX idx_proposal_analysis_recent ON proposal_analysis(analyzed_at DESC) WHERE analyzed_at > NOW() - INTERVAL '30 days';
        END IF;
    END IF;

    -- Index for comment analysis lookups
    IF EXISTS (SELECT FROM pg_tables WHERE schemaname = 'public' AND tablename = 'comment_analysis') THEN
        IF NOT EXISTS (SELECT FROM pg_indexes WHERE indexname = 'idx_comment_analysis_comment_id') THEN
            CREATE INDEX idx_comment_analysis_comment_id ON comment_analysis(comment_id);
        END IF;

        -- Index for flagged comments
        IF NOT EXISTS (SELECT FROM pg_indexes WHERE indexname = 'idx_comment_analysis_flagged') THEN
            CREATE INDEX idx_comment_analysis_flagged ON comment_analysis(is_flagged) WHERE is_flagged = true;
        END IF;

        -- Index for marketing content
        IF NOT EXISTS (SELECT FROM pg_indexes WHERE indexname = 'idx_comment_analysis_marketing') THEN
            CREATE INDEX idx_comment_analysis_marketing ON comment_analysis(is_marketing) WHERE is_marketing = true;
        END IF;
    END IF;
END$$;
