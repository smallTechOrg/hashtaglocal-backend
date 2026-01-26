# News Module Implementation Guide

## Overview
This document describes the News module implementation for hashtag local backend. The module enables importing news articles from NewsAPI.ai and exposing them through a REST API.

## Features

### 1. News Import
- Import news articles from NewsAPI.ai (EventRegistry)
- Automatic categorization into issue types (POTHOLE, WASTE, FOOTPATH, POLLUTION, HYGIENE, SAFETY)
- Duplicate detection using external article IDs
- Import job tracking and history
- Rate limiting protection (24-hour cooldown between imports)

### 2. News API
- RESTful endpoint to fetch news articles
- Pagination support
- Category filtering
- Locality-based filtering

## Database Schema

### NewsArticleEntity
```sql
CREATE TABLE news_articles (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(500) NOT NULL UNIQUE,
    title VARCHAR(1000) NOT NULL,
    description VARCHAR(2000),
    content TEXT,
    category VARCHAR(100) NOT NULL,
    source_id VARCHAR(100),
    source_name VARCHAR(255) NOT NULL,
    author VARCHAR(255),
    url VARCHAR(1000) NOT NULL,
    url_to_image VARCHAR(1000),
    published_at TIMESTAMP NOT NULL,
    locality_id BIGINT REFERENCES localities(id),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_news_locality_category ON news_articles(locality_id, category);
CREATE INDEX idx_news_published_at ON news_articles(published_at);
CREATE INDEX idx_news_external_id ON news_articles(external_id);
```

### NewsImportJob
```sql
CREATE TABLE news_import_jobs (
    id BIGSERIAL PRIMARY KEY,
    locality_id BIGINT REFERENCES localities(id),
    imported_at TIMESTAMP NOT NULL,
    articles_imported INTEGER NOT NULL,
    articles_duplicate INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL
);
```

## API Endpoints

### 1. Get News Articles
**Endpoint:** `GET /api/news/{hashtag}`

**Query Parameters:**
- `category` (optional, default: `ALL`) - Filter by issue category
  - Allowed values: `ALL`, `POTHOLE`, `WASTE`, `FOOTPATH`, `POLLUTION`, `HYGIENE`, `SAFETY`
- `page` (optional, default: `1`) - Page number (1-indexed)
- `pageSize` (optional, default: `10`) - Number of articles per page (1-100)

**Response:**
```json
{
  "status": "success",
  "totalResults": 25,
  "page": 1,
  "pageSize": 10,
  "articles": [
    {
      "id": "123",
      "title": "Pothole problem on MG Road",
      "description": "Multiple potholes reported on busy street",
      "content": "Full article content...",
      "category": "POTHOLE",
      "source": {
        "id": "the-hindu",
        "name": "The Hindu"
      },
      "author": "John Doe",
      "url": "https://example.com/article",
      "urlToImage": "https://example.com/image.jpg",
      "publishedAt": "2026-01-25T10:30:00Z",
      "location": "Bengaluru"
    }
  ]
}
```

**Examples:**
```bash
# Get all news for Bengaluru
curl http://localhost:8080/api/news/bengaluru

# Get pothole-related news
curl http://localhost:8080/api/news/bengaluru?category=POTHOLE

# Get paginated results
curl http://localhost:8080/api/news/bengaluru?page=2&pageSize=20
```

### 2. Import News (Admin Endpoint)
**Endpoint:** `POST /api/admin/news/import/{hashtag}`

**Response:**
```json
{
  "success": true,
  "message": "Import completed. Articles imported: 25, Duplicates skipped: 5",
  "jobId": 123,
  "articlesImported": 25,
  "articlesDuplicate": 5,
  "status": "SUCCESS"
}
```

**Example:**
```bash
curl -X POST http://localhost:8080/api/admin/news/import/bengaluru
```

## Configuration

### Environment Variables
Add the following to your `.env` file or environment:

```bash
NEWSAPI_API_KEY=your_api_key_here
```

### application.yaml
The configuration has been added:
```yaml
newsapi:
  api-key: ${NEWSAPI_API_KEY:}
```

## NewsAPI.ai Integration

### API Provider
- Service: NewsAPI.ai (EventRegistry)
- Documentation: https://newsapi.ai/documentation
- Registration: https://newsapi.ai/register
- Free tier: 2,000 tokens for testing

### Search Strategy
For each category, the service searches using multiple keywords:
- **POTHOLE**: pothole, road damage, crater, road defect
- **WASTE**: garbage, waste, trash, litter, dump
- **FOOTPATH**: footpath, sidewalk, pavement, pedestrian path
- **POLLUTION**: pollution, air quality, smog, emissions
- **HYGIENE**: hygiene, sanitation, cleanliness, sewage
- **SAFETY**: safety, crime, accident, danger, hazard

### Rate Limiting
- Maximum 5 simultaneous requests
- 200ms delay between requests in our implementation
- 24-hour cooldown between imports for the same locality

## Usage

### Step 1: Get Your API Key
1. Register at https://newsapi.ai/register
2. Verify your email
3. Find your API key at https://newsapi.ai/settings?tab=settings
4. Add to environment: `export NEWSAPI_API_KEY=your_key_here`

### Step 2: Import News
Run the import for a locality (hashtag):

```bash
curl -X POST http://localhost:8080/api/admin/news/import/bengaluru
```

This will:
- Fetch news from NewsAPI.ai for each category
- Filter by last 30 days
- Store articles in the database
- Track duplicates
- Return import statistics

### Step 3: Query News
Fetch news through the API:

```bash
# All news for Bengaluru
curl http://localhost:8080/api/news/bengaluru

# Only pothole news
curl http://localhost:8080/api/news/bengaluru?category=POTHOLE

# Paginated
curl "http://localhost:8080/api/news/bengaluru?page=1&pageSize=20"
```

## Implementation Details

### Categorization
Articles are categorized based on keyword matching:
1. Search is performed with locality name + keyword
2. Articles returned are tagged with the corresponding category
3. Categories align with existing `IssueTypeModel` enum

### Duplicate Detection
- Uses `externalId` (article URI from NewsAPI) as unique identifier
- Database unique constraint prevents duplicates
- Import service tracks duplicate count

### Import History
- Each import creates a `NewsImportJob` record
- Tracks success/failure status
- Records number of articles imported and duplicates
- Prevents re-importing within 24 hours

## Testing

### Unit Tests
```bash
./gradlew test --tests "NewsControllerTest"
./gradlew test --tests "NewsImportControllerTest"
./gradlew test --tests "NewsImportServiceTest"
./gradlew test --tests "NewsArticleRepositoryTest"
```

### Integration Tests
```bash
./gradlew test --tests "NewsIntegrationTest"
```

### All Tests
```bash
./gradlew test
```

## Error Handling

### API Errors
- 404: Locality not found
- 400: Invalid category or pagination parameters
- 500: Internal server error

### Import Errors
- Locality not found: Exception thrown
- API failures: Logged and job marked as FAILED
- Network issues: Handled gracefully with error logging

## Monitoring

### Import Job Status
Query the `news_import_jobs` table to see:
- Import history
- Success/failure rates
- Articles imported count
- Error messages

### Logs
Check application logs for:
- Import progress
- API call details
- Error traces

## Future Enhancements

1. **Admin Dashboard**
   - View import history
   - Manual import triggers
   - Import scheduling

2. **Advanced Filtering**
   - Date range filtering
   - Source filtering
   - Full-text search

3. **Caching**
   - Redis cache for frequently accessed news
   - Cache invalidation on new imports

4. **Analytics**
   - Track popular categories
   - Article engagement metrics
   - Source quality analysis

5. **Automated Imports**
   - Scheduled daily imports
   - Background job processing
   - Retry mechanism for failures

## Troubleshooting

### No articles imported
- Check API key is set correctly
- Verify locality exists in database
- Check NewsAPI.ai quota
- Review application logs

### Duplicate detection not working
- Verify `externalId` is being set
- Check database unique constraint
- Review import logs

### API returning empty results
- Verify locality has imported articles
- Check category filter spelling
- Verify database connection

## Security Considerations

1. **API Key Protection**
   - Never commit API keys to version control
   - Use environment variables
   - Rotate keys periodically

2. **Admin Endpoints**
   - Add authentication/authorization
   - Rate limiting for import endpoints
   - Input validation

3. **Data Privacy**
   - Store only necessary article data
   - Comply with content licensing
   - Respect robots.txt and ToS
