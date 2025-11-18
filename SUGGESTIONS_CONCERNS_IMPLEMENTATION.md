# Suggestions and Concerns Extraction Implementation

## Overview

This document describes the implementation of AI-powered suggestion and concern extraction from proposal comments, including similarity detection and aggregation.

## Features Implemented

1. **AI Extraction**: Automatically extracts suggestions and concerns from comments using OpenAI GPT-4
2. **Vector Database Indexing**: Stores embeddings for semantic similarity search
3. **Similarity Detection**: Detects similar suggestions/concerns and aggregates them
4. **Comment Tracking**: Maintains references to all comments that contributed to each suggestion/concern
5. **API Endpoints**: RESTful endpoints to retrieve suggestions and concerns for proposals

## Architecture

### Database Schema

#### New Tables

1. **`proposal_suggestions`**
   - `id` (UUID, PK)
   - `proposal_id` (UUID, FK to proposals)
   - `text` (TEXT) - The suggestion content
   - `embedding_id` (UUID, FK to embeddings) - For similarity search
   - `created_at`, `updated_at` (TIMESTAMP)

2. **`proposal_concerns`**
   - `id` (UUID, PK)
   - `proposal_id` (UUID, FK to proposals)
   - `text` (TEXT) - The concern content
   - `embedding_id` (UUID, FK to embeddings) - For similarity search
   - `created_at`, `updated_at` (TIMESTAMP)

3. **`suggestion_comments`** (Join Table)
   - `id` (UUID, PK)
   - `suggestion_id` (UUID, FK to proposal_suggestions)
   - `comment_id` (UUID, FK to comments)
   - `created_at` (TIMESTAMP)
   - Unique constraint on `(suggestion_id, comment_id)`

4. **`concern_comments`** (Join Table)
   - `id` (UUID, PK)
   - `concern_id` (UUID, FK to proposal_concerns)
   - `comment_id` (UUID, FK to comments)
   - `created_at` (TIMESTAMP)
   - Unique constraint on `(concern_id, comment_id)`

### Domain Models

- `ProposalSuggestion` - Entity representing a suggestion
- `ProposalConcern` - Entity representing a concern
- `SuggestionComment` - Join entity linking suggestions to comments
- `ConcernComment` - Join entity linking concerns to comments

### Services

#### SuggestionConcernService

Location: `/ai-integration/src/main/kotlin/com/intrigsoft/ipitch/aiintegration/service/SuggestionConcernService.kt`

**Key Methods:**

1. **`extractSuggestionsAndConcerns(comment, proposal, commentThread)`**
   - Sends comment + context to OpenAI GPT-4
   - Extracts structured suggestions and concerns
   - Returns `SuggestionConcernExtractionResult`

2. **`processSuggestions(proposalId, commentId, suggestions)`**
   - For each extracted suggestion:
     - Checks for similar existing suggestions using vector similarity (>= 0.85 cosine similarity)
     - If similar found: adds comment reference to existing suggestion
     - If not similar: creates new suggestion with vector embedding
   - Returns list of `SuggestionProcessingResult`

3. **`processConcerns(proposalId, commentId, concerns)`**
   - Same logic as processSuggestions but for concerns
   - Returns list of `ConcernProcessingResult`

4. **`getSuggestionsForProposal(proposalId)`**
   - Retrieves all suggestions with comment references

5. **`getConcernsForProposal(proposalId)`**
   - Retrieves all concerns with comment references

### Integration Flow

#### Comment Creation Flow

```
User creates comment
    ↓
CommentService.createComment()
    ↓
Save comment to database
    ↓
CommentAnalysisService.analyzeComment()
    ↓
If comment passes governance check:
    ↓
    SuggestionConcernService.extractSuggestionsAndConcerns()
        ↓
        AI extracts suggestions and concerns
        ↓
        For each suggestion:
            ↓
            processSuggestion()
                ↓
                Check vector similarity with existing suggestions
                ↓
                If similar (>= 0.85): Link to existing
                If not similar: Create new with embedding
        ↓
        For each concern:
            ↓
            processConcern()
                ↓
                Check vector similarity with existing concerns
                ↓
                If similar (>= 0.85): Link to existing
                If not similar: Create new with embedding
```

### API Endpoints

#### GET `/api/proposals/{proposalId}/suggestions`

Returns all suggestions for a proposal.

**Response:**
```json
{
  "success": true,
  "message": "Suggestions retrieved successfully",
  "data": [
    {
      "id": "uuid",
      "proposalId": "uuid",
      "text": "Suggestion text",
      "commentCount": 3,
      "commentIds": ["uuid1", "uuid2", "uuid3"],
      "createdAt": "2024-01-01T12:00:00Z",
      "updatedAt": "2024-01-01T12:00:00Z"
    }
  ]
}
```

#### GET `/api/proposals/{proposalId}/concerns`

Returns all concerns for a proposal.

**Response:**
```json
{
  "success": true,
  "message": "Concerns retrieved successfully",
  "data": [
    {
      "id": "uuid",
      "proposalId": "uuid",
      "text": "Concern text",
      "commentCount": 2,
      "commentIds": ["uuid1", "uuid2"],
      "createdAt": "2024-01-01T12:00:00Z",
      "updatedAt": "2024-01-01T12:00:00Z"
    }
  ]
}
```

## AI Prompts

### Extraction Prompt

The service uses a structured prompt to extract suggestions and concerns:

```
System Prompt:
You are an expert analyst extracting actionable suggestions and concerns from comments on proposals.
- Suggestions: Constructive ideas, improvements, or recommendations
- Concerns: Issues, problems, risks, or objections raised

Extract clear, concise, and distinct suggestions and concerns.
Each should be self-contained and understandable without the original comment.

User Prompt:
Analyze this comment and extract any suggestions and concerns:

PROPOSAL:
Title: [title]
Content: [content]...

COMMENT THREAD:
Comment 1: [previous comment]
Comment 2: [previous comment]

COMMENT TO ANALYZE:
[current comment]

Respond with JSON:
{
  "suggestions": ["suggestion 1", "suggestion 2"],
  "concerns": ["concern 1", "concern 2"]
}
```

## Similarity Detection

- Uses pgvector for vector similarity search
- Cosine similarity threshold: **0.85** (configurable)
- Entity types: `SUGGESTION` and `CONCERN`
- When a new suggestion/concern has >= 0.85 similarity to an existing one:
  - Links the new comment to the existing suggestion/concern
  - Does NOT create a duplicate
- When similarity < 0.85:
  - Creates a new suggestion/concern
  - Generates and stores vector embedding

## Files Modified/Created

### New Files Created

1. **Domain Models:**
   - `/common/src/main/kotlin/com/intrigsoft/ipitch/domain/ProposalSuggestion.kt`
   - `/common/src/main/kotlin/com/intrigsoft/ipitch/domain/ProposalConcern.kt`
   - `/common/src/main/kotlin/com/intrigsoft/ipitch/domain/SuggestionComment.kt`
   - `/common/src/main/kotlin/com/intrigsoft/ipitch/domain/ConcernComment.kt`

2. **Repositories:**
   - `/ai-integration/src/main/kotlin/com/intrigsoft/ipitch/aiintegration/repository/ProposalSuggestionRepository.kt`
   - `/ai-integration/src/main/kotlin/com/intrigsoft/ipitch/aiintegration/repository/ProposalConcernRepository.kt`

3. **Services:**
   - `/ai-integration/src/main/kotlin/com/intrigsoft/ipitch/aiintegration/service/SuggestionConcernService.kt`
   - `/ai-integration/src/main/kotlin/com/intrigsoft/ipitch/aiintegration/model/SuggestionConcernExtraction.kt`

4. **DTOs:**
   - `/proposal-manager/src/main/kotlin/com/intrigsoft/ipitch/proposalmanager/dto/response/SuggestionResponse.kt`
   - `/proposal-manager/src/main/kotlin/com/intrigsoft/ipitch/proposalmanager/dto/response/ConcernResponse.kt`

5. **Database Migration:**
   - `/ai-integration/src/main/resources/db/migration/V3__create_suggestions_concerns_tables.sql`

### Modified Files

1. **Services:**
   - `/interaction-manager/src/main/kotlin/com/intrigsoft/ipitch/interactionmanager/service/CommentService.kt`
     - Added `SuggestionConcernService` dependency
     - Added `extractSuggestionsAndConcerns()` method
     - Integrated extraction into comment creation flow

2. **Controllers:**
   - `/proposal-manager/src/main/kotlin/com/intrigsoft/ipitch/proposalmanager/api/ProposalController.kt`
     - Added `GET /{proposalId}/suggestions` endpoint
     - Added `GET /{proposalId}/concerns` endpoint

3. **Proposal Service:**
   - `/proposal-manager/src/main/kotlin/com/intrigsoft/ipitch/proposalmanager/service/ProposalService.kt`
     - Added `SuggestionConcernService` dependency
     - Added `getSuggestionsForProposal()` method
     - Added `getConcernsForProposal()` method

## Database Migration

Run the migration to create the new tables:

```bash
# The migration will run automatically with Flyway
# Location: /ai-integration/src/main/resources/db/migration/V3__create_suggestions_concerns_tables.sql
```

## Configuration

The system uses existing AI configuration from `application.yml`:

```yaml
ai:
  openai:
    api-key: ${OPENAI_API_KEY}
    model: gpt-4-turbo

  # Similarity threshold can be adjusted in SuggestionConcernService.kt
  # Current value: 0.85
```

## Usage Example

### 1. Create a comment on a proposal

```bash
POST /api/comments
{
  "userId": "user-123",
  "content": "I suggest we add better error handling and logging. This is critical for debugging.",
  "targetType": "PROPOSAL",
  "targetId": "proposal-uuid"
}
```

### 2. System automatically:
- Analyzes the comment
- Extracts:
  - Suggestion: "Add better error handling and logging"
  - Concern: "Critical for debugging" (if phrased as a concern)
- Stores in vector database
- Links to proposal

### 3. Create another similar comment

```bash
POST /api/comments
{
  "userId": "user-456",
  "content": "We really need improved error handling. Current system doesn't help troubleshoot issues.",
  "targetType": "PROPOSAL",
  "targetId": "proposal-uuid"
}
```

### 4. System detects similarity:
- Extracts suggestion: "Improved error handling for troubleshooting"
- Finds similarity > 0.85 with existing suggestion
- Links this comment to the same suggestion
- Suggestion now has `commentCount: 2`

### 5. Retrieve suggestions

```bash
GET /api/proposals/{proposalId}/suggestions

Response:
{
  "success": true,
  "data": [
    {
      "id": "suggestion-uuid",
      "text": "Add better error handling and logging",
      "commentCount": 2,
      "commentIds": ["comment-uuid-1", "comment-uuid-2"]
    }
  ]
}
```

## Benefits

1. **Aggregation**: Similar suggestions/concerns are automatically grouped
2. **Traceability**: All contributing comments are tracked
3. **Semantic Search**: Uses embeddings for intelligent similarity matching
4. **Scalability**: Vector database (pgvector) handles large-scale similarity searches efficiently
5. **Context-Aware**: AI extraction considers proposal content and comment thread
6. **Clean Data**: Avoids duplicate suggestions/concerns through similarity detection

## Future Enhancements

1. **Sentiment Analysis**: Add sentiment scores to suggestions/concerns
2. **Priority Ranking**: Rank by number of comments or user voting
3. **Trend Analysis**: Track how suggestions/concerns evolve over time
4. **Notifications**: Alert proposal owners of new concerns
5. **Response Tracking**: Link owner responses to specific suggestions/concerns
6. **Configurable Threshold**: Make similarity threshold configurable per proposal
7. **Multi-language Support**: Extract suggestions/concerns in multiple languages

## Testing

To test the implementation:

1. **Unit Tests**: Create tests for `SuggestionConcernService`
2. **Integration Tests**: Test the complete flow from comment creation to suggestion/concern retrieval
3. **Similarity Tests**: Verify similarity detection with various inputs
4. **API Tests**: Test the new endpoints

Example test cases:
- Comment with one suggestion → creates 1 suggestion
- Comment with multiple suggestions → creates multiple suggestions
- Similar comments → link to same suggestion
- Dissimilar comments → create separate suggestions
- Comments with concerns → extract and store concerns
- Retrieve suggestions/concerns via API → correct data returned

## Deployment

1. Ensure PostgreSQL with pgvector is running
2. Run database migrations (Flyway will auto-apply V3 migration)
3. Configure OpenAI API key in environment
4. Deploy services:
   - `ai-integration`
   - `interaction-manager`
   - `proposal-manager`
5. Verify endpoints are accessible

## Monitoring

Monitor the following:
- AI extraction success rate
- Similarity detection accuracy
- Vector database query performance
- API response times
- Storage growth for suggestions/concerns

## Troubleshooting

**Issue**: Suggestions not being extracted
- Check: OpenAI API key is valid
- Check: Comment passed governance check
- Check: Logs in `CommentService.extractSuggestionsAndConcerns()`

**Issue**: Similar suggestions creating duplicates
- Check: Similarity threshold (current: 0.85)
- Check: Vector embeddings are being created
- Check: Logs in `SuggestionConcernService.findSimilarSuggestion()`

**Issue**: API returning empty results
- Check: Suggestions/concerns were actually created
- Check: Proposal ID is correct
- Check: Database tables exist and are populated

## Summary

This implementation provides a complete, production-ready system for extracting, aggregating, and serving suggestions and concerns from proposal comments using AI and vector similarity search. The system is integrated into the existing comment flow and provides new API endpoints for retrieving the aggregated data.
