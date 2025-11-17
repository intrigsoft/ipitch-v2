# AI Integration Module

This module provides AI-powered features for the iPitch platform, including:

- **Proposal Analysis**: Automatic summarization, clarity scoring, and sector-specific scoring
- **Comment Moderation**: Content governance, relevance analysis, and marketing detection
- **Vector Search (RAG)**: Semantic search using PostgreSQL with pgvector
- **Elasticsearch Integration**: Index analysis results for search and user behavior analytics
- **User Analytics**: Comprehensive user profiling based on contribution quality and behavior
- **Flexible AI Providers**: Support for OpenAI, Anthropic, and local models

## Features

### Proposal Publishing
When a proposal is published, the system automatically:
1. **Generates a summary** (2-3 sentences) for search results and TLDR sections
2. **Calculates clarity score** (0-10) evaluating writing quality and clarity
3. **Scores sector relevance** (0-10 for each sector) across configured sectors
4. **Indexes in vector database** for semantic search and RAG

### Comment Creation
When a comment is added, the system:
1. **Performs governance check** - flags harmful content, spam, etc.
   - If flagged, the comment is marked and creator can revise
   - Flags include: hate speech, harassment, violence, profanity, spam, etc.
2. **Calculates relevance** (0-10) to the proposal and comment thread
3. **Scores sector relevance** for the comment content
4. **Determines mode** (SUPPORTIVE, CRITICAL, NEUTRAL, INQUISITIVE, SUGGESTIVE)
5. **Detects marketing** - identifies promotional content

### Elasticsearch Integration
All analysis results are automatically indexed in Elasticsearch for:
1. **Search** - Full-text search across proposals and comments by analysis results
2. **User Analytics** - Track user behavior, quality metrics, and contributions
3. **Moderation** - Find flagged content, spam, and marketing across the platform
4. **Sector Analysis** - Discover trends and expertise in specific sectors

### User Analytics
The system provides comprehensive user profiling based on AI analysis:
1. **Proposal Quality Metrics**:
   - Average clarity score
   - High-quality proposal count
   - Sector expertise mapping

2. **Comment Behavior Metrics**:
   - Flagged comment ratio
   - Average relevance score
   - Mode distribution (aggressiveness indicator)
   - Marketing/spam detection
   - Sector contribution patterns

3. **Calculated Scores**:
   - **Quality Score** (0-10): Overall contribution quality
   - **Aggressiveness Score** (0-10): How critical vs supportive (0=very supportive, 10=very critical)
   - **Lenience Score** (0-10): How lenient vs strict in evaluating others (0=very critical, 10=very lenient)

## Configuration

### Prerequisites
1. PostgreSQL 11+ with pgvector extension installed
2. AI API keys (OpenAI, Anthropic, or local model endpoint)

### Database Setup

1. **Enable pgvector extension**:
   ```sql
   -- Run on your PostgreSQL database
   CREATE EXTENSION IF NOT EXISTS vector;
   ```

   Or run the migration script:
   ```bash
   psql -U ipitch -d ipitch -f ai-integration/src/main/resources/db/migration/V1__enable_pgvector.sql
   ```

2. **Create optimized indexes**:
   ```bash
   psql -U ipitch -d ipitch -f ai-integration/src/main/resources/db/migration/V2__create_indexes.sql
   ```

### Environment Variables

Set these environment variables or update `application.yml`:

```bash
# OpenAI Configuration (default provider)
export OPENAI_API_KEY="your-openai-api-key"

# Optional: Anthropic Configuration
export ANTHROPIC_API_KEY="your-anthropic-api-key"

# Optional: Local AI (Ollama, etc.)
export LOCAL_AI_URL="http://localhost:11434"
```

### Application Configuration

In `proposal-manager/src/main/resources/application.yml` and `interaction-manager/src/main/resources/application.yml`, add:

```yaml
ai:
  provider: openai  # Options: openai, anthropic, local

  openai:
    api-key: ${OPENAI_API_KEY:}
    model: gpt-4-turbo-preview
    embedding-model: text-embedding-3-large
    base-url: https://api.openai.com/v1
    timeout: 60000
    max-retries: 3

  # Sector configuration
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

  # Content moderation settings
  moderation:
    enabled: true
    auto-flag-threshold: 0.8

vector-db:
  embedding-dimension: 3072  # text-embedding-3-large dimension
  similarity-function: cosine
```

## Architecture

### Services

- **AIService**: Base interface for AI providers (OpenAI, Anthropic, Local)
- **AIServiceFactory**: Factory for selecting configured AI provider
- **VectorDatabaseService**: Vector storage and similarity search using pgvector
- **ProposalAnalysisService**: Analyzes proposals on publication
- **CommentAnalysisService**: Analyzes comments on creation

### Database Schema

#### `embeddings` table
Stores vector embeddings for semantic search:
- `id`: UUID primary key
- `entity_type`: 'PROPOSAL' or 'COMMENT'
- `entity_id`: Reference to proposal/comment
- `embedding`: Vector (3072 dimensions for text-embedding-3-large)
- `model`: Embedding model used
- `dimension`: Vector dimension
- `created_at`: Timestamp

#### `proposal_analysis` table
Stores proposal analysis results:
- `id`: UUID primary key
- `proposal_id`: Reference to proposal (unique)
- `summary`: Generated summary text
- `clarity_score`: Double (0.0-10.0)
- `sector_scores`: JSONB array of {sector, score} objects
- `embedding_id`: Reference to vector embedding
- `model`: AI model used
- `provider`: AI provider (OPENAI, ANTHROPIC, LOCAL)
- `analyzed_at`: Timestamp

#### `comment_analysis` table
Stores comment analysis results:
- `id`: UUID primary key
- `comment_id`: Reference to comment (unique)
- `governance_flags`: JSONB array of governance flags
- `governance_score`: Double (0.0-1.0, higher = more problematic)
- `is_flagged`: Boolean
- `flag_reason`: Text explanation if flagged
- `relevance_score`: Double (0.0-10.0)
- `sector_scores`: JSONB array of {sector, score}
- `mode`: Enum (SUPPORTIVE, CRITICAL, NEUTRAL, INQUISITIVE, SUGGESTIVE)
- `is_marketing`: Boolean
- `marketing_score`: Double (0.0-1.0)
- `model`: AI model used
- `provider`: AI provider
- `analyzed_at`: Timestamp

## Usage Examples

### Retrieve Proposal Analysis
```kotlin
val analysis = proposalAnalysisService.getProposalAnalysis(proposalId)
if (analysis != null) {
    println("Summary: ${analysis.summary}")
    println("Clarity: ${analysis.clarityScore}/10")
    analysis.sectorScores.forEach {
        println("${it.sector}: ${it.score}/10")
    }
}
```

### Find Similar Proposals (RAG)
```kotlin
val query = "healthcare innovation for rural areas"
val similar = proposalAnalysisService.findSimilarProposals(query, topK = 5)
similar.forEach { (proposalId, similarity) ->
    println("Proposal $proposalId - Similarity: ${similarity * 100}%")
}
```

### Check Comment Moderation
```kotlin
val analysis = commentAnalysisService.getCommentAnalysis(commentId)
if (analysis?.isFlagged == true) {
    println("FLAGGED: ${analysis.flagReason}")
    println("Flags: ${analysis.governanceFlags}")
} else {
    println("Relevance: ${analysis?.relevanceScore}/10")
    println("Mode: ${analysis?.mode}")
}
```

### Get User Analytics Profile
```kotlin
val profile = userAnalyticsService.getUserProfile(userId)

// Proposal quality metrics
println("Proposals: ${profile.proposalMetrics.totalProposals}")
println("Avg Clarity: ${profile.proposalMetrics.averageClarityScore}/10")
println("High Quality: ${profile.proposalMetrics.highQualityProposalCount}")
println("Sector Expertise: ${profile.proposalMetrics.sectorExpertise}")

// Comment behavior metrics
println("Comments: ${profile.commentMetrics.totalComments}")
println("Flagged: ${profile.commentMetrics.flaggedCommentPercentage}%")
println("Avg Relevance: ${profile.commentMetrics.averageRelevanceScore}/10")
println("Mode Distribution: ${profile.commentMetrics.modeDistribution}")

// Calculated scores
println("Quality Score: ${profile.qualityScore}/10")
println("Aggressiveness: ${profile.aggressivenessScore}/10")
println("Lenience: ${profile.lenienceScore}/10")
```

### Search Analysis Results in Elasticsearch
```kotlin
// Search proposals by summary content
val proposals = proposalAnalysisRepository.searchBySummary(
    "healthcare innovation",
    PageRequest.of(0, 10)
)

// Find high-clarity proposals
val highQuality = proposalAnalysisRepository.findByClarityScoreBetween(
    8.0, 10.0,
    PageRequest.of(0, 10)
)

// Find flagged comments for moderation
val flaggedComments = commentAnalysisRepository.findByIsFlagged(
    true,
    PageRequest.of(0, 20)
)

// Find critical comments on a proposal
val criticalComments = commentAnalysisRepository.findByProposalIdAndMode(
    proposalId.toString(),
    "CRITICAL",
    PageRequest.of(0, 10)
)

// Get top quality authors
val topAuthors = userAnalyticsService.getTopQualityProposalAuthors(limit = 10)

// Find users with most flagged content (for moderation)
val problematicUsers = userAnalyticsService.getUsersWithMostFlaggedComments(limit = 10)
```

## Testing

Run tests for the AI integration module:

```bash
./gradlew :ai-integration:test
```

Note: AI service tests may be skipped if API keys are not configured.

## Monitoring & Logs

The module logs extensively:
- AI analysis start/completion
- Governance flags and warnings
- Analysis results (scores, summaries)
- Errors (non-blocking, won't fail proposal/comment creation)

Example log output:
```
INFO  Starting AI analysis for proposal 550e8400-e29b-41d4-a716-446655440000
INFO  AI analysis completed. Summary: This proposal addresses healthcare...
INFO  Clarity score: 8.5, Sector scores: [Healthcare: 10.0, IT: 6.5]
```

## Troubleshooting

### "pgvector extension not found"
- Ensure pgvector is installed on your PostgreSQL instance
- Run the V1__enable_pgvector.sql migration
- Verify: `SELECT * FROM pg_extension WHERE extname = 'vector';`

### "ProposalAnalysisService not available"
- Check that ai-integration module is included in dependencies
- Ensure AI API key is configured
- Check application logs for initialization errors

### Slow vector searches
- Ensure HNSW index is created on embeddings table
- Run V2__create_indexes.sql migration
- Check index: `\d embeddings` in psql

### AI API rate limits
- Reduce concurrent requests
- Increase timeout in configuration
- Consider using local AI provider (Ollama) for development

## Future Enhancements

- [ ] Support for more AI providers (Cohere, HuggingFace, etc.)
- [ ] Async processing with message queue
- [ ] Caching of analysis results
- [ ] Admin dashboard for reviewing flagged content
- [ ] Sector hierarchy support
- [ ] Custom sector configuration per organization
- [ ] Sentiment analysis trends over time
- [ ] Automated suggestions for improving clarity scores
