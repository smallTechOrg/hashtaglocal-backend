# Indian City Polygon Import

This feature allows you to import polygon boundaries for all major Indian cities into the Localities table using Google Maps Geocoding API. **All imports are tracked in the database with detailed status and retry capabilities.**

## Features

- Import 100+ major Indian cities with their geographic boundaries
- **Track all import jobs in the database** with detailed status
- **Track individual city import attempts** with error messages
- **Automatic retry for failed cities** with one command
- Uses Google Maps Geocoding API for reliable, high-quality data for Indian cities
- Automatically creates hashtags for each city (e.g., `#Mumbai`, `#Delhi`)
- Creates bounding box polygons from city viewport/bounds data
- Fast rate limiting (10 requests per second)
- Checks for existing cities to avoid duplicates
- Can import all cities at once or individual cities
- View import history and detailed failure reasons

## Components

### 1. Database Tables
- **import_jobs** - Tracks each import job run with status, counts, and timestamps
- **city_import_status** - Tracks individual city import attempts with errors and retry counts

### 2. IndianCityPolygonService
- Fetches city polygon boundaries from Google Maps Geocoding API
- Creates bounding box polygons from viewport/bounds data
- Contains list of 100+ major Indian cities
- Handles rate limiting (10 requests per second)

### 3. IndianCityImportJob
- CommandLineRunner that can be triggered with `--import-cities=true` flag
- Imports all cities sequentially with database tracking
- Provides detailed logging and summary
- Supports retrying failed imports

### 4. LocalityImportController
- REST API endpoints for manual import and monitoring
- `/api/admin/localities/import-all-cities` - Import all cities
- `/api/admin/localities/import-city?cityName=Mumbai` - Import single city
- `/api/admin/localities/retry-failed-cities` - Retry all failed cities
- `/api/admin/localities/import-jobs` - View all import jobs
- `/api/admin/localities/import-jobs/{id}` - View detailed job status

## Prerequisites

### Google Maps API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the **Geocoding API**
4. Create credentials (API Key)
5. Set the API key as an environment variable:
   ```bash
   export GOOGLE_MAPS_API_KEY="your-api-key-here"
   ```

### API Pricing
- Google Maps Geocoding API: First 40,000 requests/month free, then $5 per 1,000 requests
- Importing 100 cities = 100 requests (well within free tier)
- For production: Consider setting up billing and usage limits

## Usage

### Method 1: Command Line (Recommended for initial bulk import)

1. Set your Google Maps API key:
```bash
export GOOGLE_MAPS_API_KEY="your-api-key-here"
```

2. Run the import job using Gradle:
```bash
./gradlew bootRun --args='--import-cities=true'
```

This will:
- Fetch polygon boundaries for all 100+ cities from Google Maps
- Save them to the Localities table
- Respect rate limits (takes ~20 seconds total)
- Show progress and summary

### Method 2: REST API (For individual cities or manual triggers)

#### Import all cities:
```bash
curl -X POST http://localhost:8080/api/admin/localities/import-all-cities
```

#### Import a single city:
```bash
curl -X POST "http://localhost:8080/api/admin/localities/import-city?cityName=Mumbai"
```

### Method 3: Programmatically

Inject `IndianCityImportJob` and call:
```java
@Autowired
private IndianCityImportJob importJob;

// Import all cities
importJob.importIndianCities();

// Import single city
importJob.importCity("Bengaluru");
```
# Retry all failed cities:
```bash
curl -X POST http://localhost:8080/api/admin/localities/retry-failed-cities
```

#### View all import jobs:
```bash
curl http://localhost:8080/api/admin/localities/import-jobs
```

#### View specific import job with all city statuses:
```bash
curl http://localhost:8080/api/admin/localities/import-jobs/1
```

###
## Cities Included

Th Database Schema

### localities table
Cities are stored in the `localities` table with:
- `id` (auto-generated)
- `name` (city name, e.g., "Mumbai")
- `hashtag` (unique, e.g., "#Mumbai")
- `geo_boundary` (PostGIS Polygon with SRID 4326)

### import_jobs table
Tracks each import run:
- `id` (auto-generated)
- `started_at` (timestamp)
- `completed_at` (timestamp)
- `status` (RUNNING, COMPLETED, FAILED, CANCELLED)
- `total_cities` (total number of cities to import)
- `success_count` (number of successful imports)
- `failure_count` (number of failed imports)
- `skipped_count` (number of skipped cities)

### city_import_status table
Tracks each city import attempt:
- `id` (auto-generated)
- `import_job_id` (foreign key to import_jobs)
- `city_name` (e.g., "Mumbai")
- `status` (PENDING, SUCCESS, FAILED, SKIPPED, NO_DATA_FOUND, RATE_LIMITED)
- `attempted_at` (timestamp)
- `completed_at` (timestamp)
- `attempt_count` (number of retry attempts)
- `error_message` (if import failed)
- `locality_id` (foreign key to localities, if successful)

## Example Output
If any cities fail during import (network issues, API errors, etc.), you can retry them:

### Command Line:
```bash
./gradlew bootRun --args='--import-cities=true'
# Then use the API to retry:
curl -X POST http://localhost:8080/api/admin/localities/retry-failed-cities
```

### Or directly via API:
```bash
curl -X POST http://localhost:8080/api/admin/localities/retry-failed-cities
```

This will:
- Query all cities with FAILED or NO_DATA_FOUND status
- Attempt to import them again
- Update attempt counts and error messages
- Show summary of retry results

## Error Handling

- Automatically skips cities that already exist
- Logs all errors with detailed messages
- Provides summary at the end with failure reasons
- Failed cities can be retried individually or in bulk
- All errors tracked in `city_import_status` table

## Troubleshooting

### Issue: "Google Maps API key not configured"
- Set the environment variable: `export GOOGLE_MAPS_API_KEY="your-key"`
- Or add to `application.yaml`: `google.maps.api-key: your-key`
- Verify the key is valid and has Geocoding API enabled

### Issue: Cities not getting imported
- Check database connection
- Verify PostGIS is installed and enabled
- Check application logs for specific errors
- Verify Google Maps API key has Geocoding API enabled
- Check if you've exceeded API quota

### Issue: API quota exceeded
- Check your [Google Cloud Console](https://console.cloud.google.com/) for usage
- Free tier provides 40,000 requests/month
- Enable billing for higher limits if needed
- Use retry mechanism to continue after quota reset

### Issue: Viewing import status
```bash
# Get all import jobs
curl http://localhost:8080/api/admin/localities/import-jobs

# Get details of specific job
curl http://localhost:8080/api/admin/localities/import-jobs/1

# Query database directly
SELECT * FROM import_jobs ORDER BY started_at DESC;
SELECT * FROM city_import_status WHERE status = 'FAILED';
```

### Issue: Testing without API key
- Test endpoints will need mocking for CI/CD
- Use `@TestPropertySource` to inject test API key
- Or mock the `IndianCityPolygonService` in tests
```bash
./gradlew bootRun --args='--import-cities=true'
# Then use the API to retry:
curl -X POST http://localhost:8080/api/admin/localities/retry-failed-cities
```

### Or directly via API:
- Query `city_import_status` table for detailed error messages:
  ```sql
  SELECT * FROM city_import_status WHERE status IN ('FAILED', 'NO_DATA_FOUND');
  ```

### Issue: Many cities failing
- Check the specific error messages in `city_import_status` table
- Use the retry endpoint after fixing any issues
- Some cities may have incorrect names - try alternative spellings

### Issue: Rate limit errors
- The service already handles rate limiting
- If you see 429 errors, the wait time may need to be increased
- Check if you're running multiple imports simultaneously

### Issue: Viewing import status
```bash
# Get all import jobs
curl http://localhost:8080/api/admin/localities/import-jobs

# Get details of specific job
curl http://localhost:8080/api/admin/localities/import-jobs/1

# Query database directly
SELECT * FROM import_jobs ORDER BY started_at DESC;
SELECT * FROM city_import_status WHERE status = 'FAILED';
``` API callscks each city import attempt:
- `id` (auto-generated)
- `import_job_id` (foreign key to import_jobs)
- `city_name` (e.g., "Mumbai")
- `status` (PENDING, SUCCESS, FAILED, SKIPPED, NO_DATA_FOUND, RATE_LIMITED)
- `attempted_at` (timestamp)
- `completed_at` (timestamp)
- `attempt_count` (number of retry attempts)
- `error_message` (if import failed)
- `locality_id` (foreign key to localities, if successfulminutes

## Error Handling

- Automatically skips cities that already exist
- Logs failed imports
- Use Google Maps Places API for more detailed boundaries
- Implement polygon simplification for large boundaries
- Cache API responses to reduce API calls
- Support for other countries
- Validation of imported polygons
- Admin UI for managing imports
- Webhook notifications for import completion

## Notes

- First-time import of all cities takes ~20 seconds
- Subsequent runs skip existing cities (very fast)
- Safe to run multiple times
- Uses PostGIS for efficient spatial queries
- Polygon coordinates use WGS84 (SRID 4326)
- Bounding boxes are approximate but sufficient for most locality-based features
- Google Maps provides consistently reliable data for Indian cities
2026-01-22 10:00:01 INFO  Processing city 1/100: Mumbai
2026-01-22 10:00:02 INFO  Successfully imported city: Mumbai with hashtag: #Mumbai
2026-01-22 10:00:03 INFO  Processing city 2/100: Delhi
2026-01-22 10:00:04 INFO  Successfully imported city: Delhi with hashtag: #Delhi
...
========== Import Summary ==========
Total cities: 100
Successfully imported: 98
Failed: 2
Failed cities: [SomeCity1, SomeCity2]
===================================
```

## Troubleshooting

### Issue: Cities not getting imported
- Check database connection
- Verify PostGIS is installed and enabled
- Check application logs for specific errors

### Issue: Rate limit errors
- The service already handles rate limiting
- If you see 429 errors, the wait time may need to be increased

### Issue: Polygon parsing errors
- Some cities may have complex MultiPolygon geometries
- The service takes the largest polygon from MultiPolygons
- Check logs for specific city errors

## Future Enhancements

Potential improvements:
- Add support for districts and smaller localities
- Implement polygon simplification for large boundaries
- Add retry mechanism for failed imports
- Cache API responses
- Support for other countries
- Validation of imported polygons
- Admin UI for managing imports

## Notes

- First-time import of all cities takes ~2-3 minutes
- Subsequent runs skip existing cities (very fast)
- Safe to run multiple times
- Uses PostGIS for efficient spatial queries
- Polygon coordinates use WGS84 (SRID 4326)
