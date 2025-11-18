-- Add dirty field to users table to track when scores need recalculation
ALTER TABLE users ADD COLUMN IF NOT EXISTS dirty BOOLEAN NOT NULL DEFAULT false;

-- Add index on dirty field for efficient querying of dirty users
CREATE INDEX IF NOT EXISTS idx_users_dirty ON users(dirty) WHERE dirty = true;

-- Comment on column
COMMENT ON COLUMN users.dirty IS 'Indicates whether user scores need recalculation due to new comments or proposals';
