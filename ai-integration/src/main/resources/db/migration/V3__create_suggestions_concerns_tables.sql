-- Create tables for proposal suggestions and concerns
-- These tables store extracted suggestions and concerns from comments
-- with support for similarity detection and comment tracking

-- Create proposal_suggestions table
CREATE TABLE IF NOT EXISTS proposal_suggestions (
    id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL,
    text TEXT NOT NULL,
    embedding_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign key to embeddings table for similarity search
    CONSTRAINT fk_suggestions_embedding FOREIGN KEY (embedding_id) REFERENCES embeddings(id) ON DELETE SET NULL
);

-- Create proposal_concerns table
CREATE TABLE IF NOT EXISTS proposal_concerns (
    id UUID PRIMARY KEY,
    proposal_id UUID NOT NULL,
    text TEXT NOT NULL,
    embedding_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign key to embeddings table for similarity search
    CONSTRAINT fk_concerns_embedding FOREIGN KEY (embedding_id) REFERENCES embeddings(id) ON DELETE SET NULL
);

-- Create suggestion_comments join table
CREATE TABLE IF NOT EXISTS suggestion_comments (
    id UUID PRIMARY KEY,
    suggestion_id UUID NOT NULL,
    comment_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign key to proposal_suggestions
    CONSTRAINT fk_suggestion_comments_suggestion FOREIGN KEY (suggestion_id) REFERENCES proposal_suggestions(id) ON DELETE CASCADE,

    -- Unique constraint to prevent duplicate comment-suggestion links
    CONSTRAINT uk_suggestion_comment UNIQUE (suggestion_id, comment_id)
);

-- Create concern_comments join table
CREATE TABLE IF NOT EXISTS concern_comments (
    id UUID PRIMARY KEY,
    concern_id UUID NOT NULL,
    comment_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign key to proposal_concerns
    CONSTRAINT fk_concern_comments_concern FOREIGN KEY (concern_id) REFERENCES proposal_concerns(id) ON DELETE CASCADE,

    -- Unique constraint to prevent duplicate comment-concern links
    CONSTRAINT uk_concern_comment UNIQUE (concern_id, comment_id)
);

-- Create indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_suggestions_proposal_id ON proposal_suggestions(proposal_id);
CREATE INDEX IF NOT EXISTS idx_suggestions_embedding_id ON proposal_suggestions(embedding_id);
CREATE INDEX IF NOT EXISTS idx_concerns_proposal_id ON proposal_concerns(proposal_id);
CREATE INDEX IF NOT EXISTS idx_concerns_embedding_id ON proposal_concerns(embedding_id);
CREATE INDEX IF NOT EXISTS idx_suggestion_comments_suggestion_id ON suggestion_comments(suggestion_id);
CREATE INDEX IF NOT EXISTS idx_suggestion_comments_comment_id ON suggestion_comments(comment_id);
CREATE INDEX IF NOT EXISTS idx_concern_comments_concern_id ON concern_comments(concern_id);
CREATE INDEX IF NOT EXISTS idx_concern_comments_comment_id ON concern_comments(comment_id);

-- Comments for documentation
COMMENT ON TABLE proposal_suggestions IS 'Stores suggestions extracted from comments on proposals. Multiple comments may contribute to the same suggestion via similarity matching.';
COMMENT ON TABLE proposal_concerns IS 'Stores concerns extracted from comments on proposals. Multiple comments may contribute to the same concern via similarity matching.';
COMMENT ON TABLE suggestion_comments IS 'Join table linking suggestions to the comments that contributed to them.';
COMMENT ON TABLE concern_comments IS 'Join table linking concerns to the comments that contributed to them.';
COMMENT ON COLUMN proposal_suggestions.embedding_id IS 'Reference to vector embedding in embeddings table for similarity search.';
COMMENT ON COLUMN proposal_concerns.embedding_id IS 'Reference to vector embedding in embeddings table for similarity search.';
