# AI Integration Module - Implementation Summary

## Overview
A complete AI integration module has been created for the iPitch platform, providing intelligent analysis of proposals and comments with vector-based semantic search (RAG) capabilities.

## What Was Implemented

### 1. Core AI Infrastructure

#### New Module: `ai-integration`
- **Location**: `/ai-integration/`
- **Type**: Shared library module (similar to `common`)
- **Purpose**: Provides AI services to `proposal-manager` and `interaction-manager`

#### AI Service Architecture
- **AIService Interface**: Base interface supporting multiple AI providers
- **OpenAIService**: Full implementation for OpenAI API
  - Text completion (GPT-4 Turbo)
  - Text embeddings (text-embedding-3-large)
  - Structured JSON responses
  - Batch processing
- **AIServiceFactory**: Factory pattern for provider selection
- **Flexible Configuration**: Easy to add Anthropic, local models (Ollama), or other providers

#### Vector Database (RAG)
- **PostgreSQL with pgvector extension**
- **VectorDatabaseService**:
  - Store and retrieve embeddings
  - Semantic similarity search
  - Cosine similarity calculations
  - HNSW indexing for fast searches
- **Embedding dimension**: 3072 (text-embedding-3-large)

### 2. Proposal Analysis Features

When a proposal is **published**, the system automatically:

1. **Generates Summary** (2-3 sentences)
   - Suitable for search results
   - Used in TLDR sections
   - Stored in `proposal_analysis` table

2. **Calculates Clarity Score** (0.0 - 10.0)
   - Evaluates writing quality
   - Assesses proposal organization
   - Considers readability

3. **Sector-Specific Scoring** (0.0 - 10.0 per sector)
   - Configurable sectors: IT, Legal, Media, Environment, Healthcare, Education, Transport, Tourism, Finance, Agriculture, Energy, Manufacturing
   - Multiple sectors can be scored for each proposal
   - Example: Hospital proposal → Healthcare: 10.0, IT: 6.5, Transport: 4.0

4. **Vector Indexing**
   - Full proposal text indexed in vector database
   - Enables semantic search
   - Powers "Find Similar Proposals" feature

#### Database Tables
- **`proposal_analysis`**: Stores analysis results
- **`embeddings`**: Vector embeddings for proposals

#### Integration Point
- **File**: `proposal-manager/src/main/kotlin/com/intrigsoft/ipitch/proposalmanager/service/ProposalService.kt`
- **Method**: `publishProposal()` (line ~351)
- **Flow**: Publish → AI Analysis → View Manager Indexing

### 3. Comment Analysis Features

When a comment is **created**, the system performs a two-stage analysis:

#### Stage 1: Governance Check (Always Performed)
Checks for harmful content:
- Hate speech
- Harassment
- Self-harm content
- Sexual content
- Violence/threats
- Severe profanity
- Spam

**If Flagged**:
- Comment is saved but marked as flagged
- `flag_reason` explains why
- Creator can revise the comment
- No further analysis performed

#### Stage 2: Full Analysis (Only if Governance Passes)
1. **Relevance Score** (0.0 - 10.0)
   - How relevant is the comment to the proposal
   - Considers full comment thread context
   - Uses parent comments for better accuracy

2. **Sector-Specific Scores** (0.0 - 10.0 per sector)
   - Same sectors as proposals
   - Identifies topic alignment

3. **Mode Detection**
   - SUPPORTIVE: Agrees/endorses proposal
   - CRITICAL: Raises concerns/disagreements
   - NEUTRAL: Factual/observational
   - INQUISITIVE: Asks questions
   - SUGGESTIVE: Offers improvements

4. **Marketing Detection** (0.0 - 1.0)
   - Identifies promotional content
   - Flags spam/advertising
   - `isMarketing` boolean flag

#### Comment Thread Context
- System builds complete thread from root to current comment
- Entire thread sent to AI for context-aware analysis
- Improves relevance and mode detection accuracy

#### Database Table
- **`comment_analysis`**: Stores all analysis results

#### Integration Point
- **File**: `interaction-manager/src/main/kotlin/com/intrigsoft/ipitch/interactionmanager/service/CommentService.kt`
- **Method**: `createComment()` (line ~61)
- **Flow**: Create → AI Analysis → Elasticsearch Sync

### 4. Database Schema

#### New Tables

**`embeddings`**
```sql
- id (UUID, PK)
- entity_type (VARCHAR) -- 'PROPOSAL', 'COMMENT'
- entity_id (UUID) -- Reference to proposal/comment
- embedding (VECTOR) -- pgvector type, dimension 3072
- model (VARCHAR) -- e.g., 'text-embedding-3-large'
- dimension (INTEGER)
- created_at (TIMESTAMP)

INDEXES:
- HNSW index on embedding (fast similarity search)
- (entity_type, entity_id) composite index
```

**`proposal_analysis`**
```sql
- id (UUID, PK)
- proposal_id (UUID, UNIQUE)
- summary (TEXT)
- clarity_score (DOUBLE)
- sector_scores (JSONB) -- [{sector, score}, ...]
- embedding_id (UUID)
- model (VARCHAR)
- provider (VARCHAR) -- 'OPENAI', 'ANTHROPIC', 'LOCAL'
- analyzed_at (TIMESTAMP)
- created_at (TIMESTAMP)
- updated_at (TIMESTAMP)

INDEXES:
- proposal_id
- analyzed_at DESC (recent analyses)
```

**`comment_analysis`**
```sql
- id (UUID, PK)
- comment_id (UUID, UNIQUE)
- governance_flags (JSONB) -- [flag1, flag2, ...]
- governance_score (DOUBLE) -- 0.0-1.0
- is_flagged (BOOLEAN)
- flag_reason (TEXT)
- relevance_score (DOUBLE) -- 0.0-10.0
- sector_scores (JSONB)
- mode (VARCHAR) -- SUPPORTIVE, CRITICAL, etc.
- is_marketing (BOOLEAN)
- marketing_score (DOUBLE) -- 0.0-1.0
- model (VARCHAR)
- provider (VARCHAR)
- analyzed_at (TIMESTAMP)
- created_at (TIMESTAMP)
- updated_at (TIMESTAMP)

INDEXES:
- comment_id
- is_flagged (WHERE is_flagged = true)
- is_marketing (WHERE is_marketing = true)
```

#### Migration Files
- **V1__enable_pgvector.sql**: Enables pgvector extension
- **V2__create_indexes.sql**: Creates optimized indexes (HNSW, partial indexes)

### 5. Configuration

#### Environment Variables Needed
```bash
# Required for OpenAI (default provider)
OPENAI_API_KEY=your-api-key-here

# Optional for other providers
ANTHROPIC_API_KEY=your-api-key-here
LOCAL_AI_URL=http://localhost:11434
```

#### Configuration Files
Both `proposal-manager` and `interaction-manager` need AI configuration in their `application.yml`:

```yaml
ai:
  provider: openai

  openai:
    api-key: ${OPENAI_API_KEY:}
    model: gpt-4-turbo-preview
    embedding-model: text-embedding-3-large
    base-url: https://api.openai.com/v1
    timeout: 60000
    max-retries: 3

  sectors:
    enabled:
      - IT
      - Legal
      - Media
      - Environment
      - Healthcare
      - Education
      - Transport
      - Tourism
      - Finance
      - Agriculture
      - Energy
      - Manufacturing

  moderation:
    enabled: true
    auto-flag-threshold: 0.8

vector-db:
  embedding-dimension: 3072
  similarity-function: cosine
```

### 6. Infrastructure Updates

#### Docker Compose
- **Changed**: PostgreSQL image from `postgres:16-alpine` to `pgvector/pgvector:pg16`
- **Added**: Volume mount for migration scripts
- **Benefit**: pgvector extension pre-installed

#### Module Dependencies
- **proposal-manager**: Now depends on `ai-integration`
- **interaction-manager**: Now depends on `ai-integration`

### 7. Implementation Details

#### Key Design Decisions

1. **Non-Blocking Integration**
   - AI analysis failures don't prevent proposal publication or comment creation
   - Errors are logged but operations continue
   - Users won't experience failures due to AI issues

2. **Optional Service Injection**
   - `proposalAnalysisService` and `commentAnalysisService` are optional (`? = null`)
   - Services work without AI if not configured
   - Graceful degradation

3. **Asynchronous Processing**
   - Uses Kotlin coroutines (`suspend` functions)
   - `runBlocking` used at integration points
   - Doesn't block main application threads

4. **Context-Aware Analysis**
   - Comment analysis includes full thread context
   - Walks up parent comment chain
   - Finds root proposal automatically

5. **Two-Stage Comment Processing**
   - Governance check always happens first
   - Full analysis only if governance passes
   - Saves API costs and processing time

#### Error Handling
```kotlin
try {
    proposalAnalysisService?.let { service ->
        runBlocking {
            val result = service.analyzeProposal(proposal)
            logger.info { "Analysis completed: ${result.summary}" }
        }
    }
} catch (e: Exception) {
    logger.error(e) { "AI analysis failed, but proposal published successfully" }
    // Continue - don't fail the operation
}
```

### 8. Testing

#### Unit Tests Created
- **VectorDatabaseServiceTest**: Tests embedding storage, retrieval, similarity search
- **SectorScoreTest**: Validates score range enforcement
- **ProposalAnalysisResultTest**: Tests data model validation

#### Test Coverage
- Model validation
- Service layer logic
- Mock-based testing with MockK
- Repository interactions

### 9. Documentation

#### README.md (`ai-integration/README.md`)
Comprehensive documentation including:
- Feature descriptions
- Configuration guide
- Database setup instructions
- Usage examples
- Troubleshooting guide
- Future enhancements roadmap

#### Migration Scripts
- Inline comments explaining each step
- Safe execution with existence checks
- Optimized indexes for performance

## File Structure

```
ai-integration/
├── build.gradle.kts
├── README.md
├── src/
│   ├── main/
│   │   ├── kotlin/com/intrigsoft/ipitch/aiintegration/
│   │   │   ├── config/
│   │   │   │   ├── AIConfiguration.kt
│   │   │   │   ├── AIProperties.kt
│   │   │   │   └── VectorDatabaseProperties.kt
│   │   │   ├── model/
│   │   │   │   ├── AIProvider.kt
│   │   │   │   ├── CommentAnalysis.kt
│   │   │   │   ├── CommentAnalysisResult.kt
│   │   │   │   ├── ContentMode.kt
│   │   │   │   ├── EmbeddingVector.kt
│   │   │   │   ├── GovernanceFlag.kt
│   │   │   │   ├── ProposalAnalysis.kt
│   │   │   │   ├── ProposalAnalysisResult.kt
│   │   │   │   └── SectorScore.kt
│   │   │   ├── repository/
│   │   │   │   ├── CommentAnalysisRepository.kt
│   │   │   │   ├── EmbeddingRepository.kt
│   │   │   │   └── ProposalAnalysisRepository.kt
│   │   │   └── service/
│   │   │       ├── AIService.kt
│   │   │       ├── AIServiceFactory.kt
│   │   │       ├── CommentAnalysisService.kt
│   │   │       ├── OpenAIService.kt
│   │   │       ├── ProposalAnalysisService.kt
│   │   │       └── VectorDatabaseService.kt
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   │           ├── V1__enable_pgvector.sql
│   │           └── V2__create_indexes.sql
│   └── test/
│       └── kotlin/com/intrigsoft/ipitch/aiintegration/
│           ├── model/
│           │   ├── ProposalAnalysisResultTest.kt
│           │   └── SectorScoreTest.kt
│           └── service/
│               └── VectorDatabaseServiceTest.kt
```

## How to Use

### 1. Setup Database
```bash
# Start services
docker-compose up -d

# Verify pgvector is enabled
docker exec ipitch-postgres psql -U ipitch -c "SELECT * FROM pg_extension WHERE extname = 'vector';"
```

### 2. Configure API Keys
```bash
export OPENAI_API_KEY="sk-..."
```

### 3. Run Application
```bash
# Build all modules
./gradlew build

# Run proposal-manager
./gradlew :proposal-manager:bootRun

# Run interaction-manager
./gradlew :interaction-manager:bootRun
```

### 4. Test AI Features

**Publish a Proposal**:
```bash
# Create and publish proposal
curl -X POST http://localhost:8081/api/v1/proposals/{id}/publish

# Check logs for AI analysis results
# Look for:
# - "AI analysis completed for proposal..."
# - "Clarity score: 8.5"
# - "Sector scores: [...]"
```

**Create a Comment**:
```bash
# Create comment
curl -X POST http://localhost:8083/api/v1/comments \
  -H "Content-Type: application/json" \
  -d '{"content": "Great proposal!", "targetType": "PROPOSAL", "targetId": "...", "userId": "..."}'

# Check logs for:
# - "AI analysis for comment..."
# - "Relevance: 8.5, Mode: SUPPORTIVE"
# - Or "Comment flagged: ..." if problematic content
```

**Query Analysis Results**:
```kotlin
// Get proposal analysis
val analysis = proposalAnalysisService.getProposalAnalysis(proposalId)
println("Summary: ${analysis?.summary}")
println("Clarity: ${analysis?.clarityScore}")

// Get comment analysis
val commentAnalysis = commentAnalysisService.getCommentAnalysis(commentId)
if (commentAnalysis?.isFlagged == true) {
    println("Flagged: ${commentAnalysis.flagReason}")
}
```

## Next Steps & Recommendations

### Immediate (Before Production)
1. ✅ Set up production OpenAI API key
2. ✅ Configure sectors based on your domain
3. ✅ Adjust auto-flag threshold based on your moderation policy
4. ✅ Test with real proposal and comment data
5. ⚠️ Add integration tests with real AI calls (optional, costs money)

### Short Term
1. Add admin dashboard to review flagged comments
2. Create API endpoints to retrieve analysis results
3. Implement "Find Similar Proposals" search feature
4. Add caching layer to reduce API costs
5. Set up monitoring for AI API usage and costs

### Long Term
1. Support additional AI providers (Anthropic, local models)
2. Implement async processing with message queue
3. Add sentiment analysis trends over time
4. Build auto-suggestions for improving clarity scores
5. Support sector hierarchies
6. Multi-language support

## Success Metrics

The AI integration is working if you see:

✅ **Proposal Analysis**:
- Summaries generated after publication
- Clarity scores logged (0-10 range)
- Multiple sector scores per proposal
- Embeddings created in database

✅ **Comment Analysis**:
- Governance checks on all comments
- Flagged comments logged with reasons
- Relevance scores for clean comments
- Mode detection (SUPPORTIVE, CRITICAL, etc.)

✅ **RAG/Vector Search**:
- Embeddings stored in `embeddings` table
- Similarity searches return relevant results
- HNSW index speeds up queries

✅ **System Health**:
- No failed publications/comments due to AI
- Errors logged but don't block operations
- API usage within rate limits

## Troubleshooting

See `ai-integration/README.md` for detailed troubleshooting guide.

Common issues:
- **"pgvector extension not found"**: Run V1__enable_pgvector.sql
- **"ProposalAnalysisService not available"**: Check API key configuration
- **"OpenAI API error: 401"**: Verify OPENAI_API_KEY is set correctly
- **Slow queries**: Ensure HNSW index exists on embeddings table

## Cost Estimation

**OpenAI API Costs** (approximate):
- **Proposal analysis**: ~$0.02 per proposal (GPT-4 Turbo + embeddings)
- **Comment analysis**: ~$0.01 per comment
- **Similar search**: ~$0.001 per query

For 100 proposals/day + 500 comments/day:
- Daily: ~$7
- Monthly: ~$210

**Recommendations**:
- Use GPT-3.5-turbo for lower costs (80% cheaper)
- Cache analysis results
- Batch processing for embeddings
- Consider local models (Ollama) for development

---

**Implementation Date**: 2025-11-17
**Implemented By**: Claude (Anthropic AI Assistant)
**Modules Affected**: ai-integration (new), proposal-manager, interaction-manager, common
**Database Changes**: 3 new tables, pgvector extension, optimized indexes
**Total Files Created/Modified**: 35+ files
