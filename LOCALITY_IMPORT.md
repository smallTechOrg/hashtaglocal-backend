# Indian City Polygon Import

This feature allows you to import polygon boundaries for all major Indian cities into the Localities table using OpenStreetMap data via the Nominatim API. **All imports are now tracked in the database with detailed status and retry capabilities.**

## Features

- Import 100+ major Indian cities with their geographic boundaries
- **Track all import jobs in the database** with detailed status
- **Track individual city import attempts** with error messages
- **Automatic retry for failed cities** with one command
- Uses OpenStreetMap's free and open-source data via Nominatim API
- Automatically creates hashtags for each city (e.g., `#Mumbai`, `#Delhi`)
- Handles both Polygon and MultiPolygon geometries
- Respects Nominatim rate limits (1 request per second)
- Checks for existing cities to avoid duplicates
- Can import all cities at once or individual cities
- View import history and detailed failure reasons

## Components

### 1. Database Tables
- **import_jobs** - Tracks each import job run with status, counts, and timestamps
- **city_import_status** - Tracks individual city import attempts with errors and retry counts

### 2. IndianCityPolygonService
- Fetches city polygon boundaries from Nominatim API
- Converts GeoJSON to JTS Polygon format
- Contains list of 100+ major Indian cities
- Handles rate limiting

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

## Usage

### Method 1: Command Line (Recommended for initial bulk import)

Run the import job using Gradle:

```bash
./gradlew bootRun --args='--import-cities=true'
```

This will:
- Fetch polygon boundaries for all 100+ cities
- Save them to the Localities table
- Respect rate limits (takes ~2 minutes total)
- Show progress and summary

### Method 2: REST API (For individual cities or manual triggers)

###

#### Retry all failed cities:
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
```# Import all cities:
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

## Cities Included

The import includes:
- All metro cities (Mumbai, Delhi, Bengaluru, etc.)
- All state capitals
- All tier-1 and tier-2 cities
- Major district headquarters
- Total: 100+ cities covering all major urban areas in India

## Data Source

- **Source**: OpenStreetMap via Nominatim API
- **License**: ODbL (Open Database License)
- **Attribution**: © OpenStreetMap contributors
- **API**: https://nominatim.openstreetmap.org

## Rate Limiting
### localities table
Cities are stored in the `localities` table with:
- `id` (auto-generated)
- `name` (city name, e.g., "Mumbai")
- `hashtag` (unique, e.g., "#Mumbai")
- `geo_boundary` (PostGIS Polygon with SRID 4326)

### import_jobs table
Tracks each import run:
- `id` (auto-generated)Started import job #1 for 100 cities
2026-01-22 10:00:01 INFO  Processing city 1/100: Mumbai
2026-01-22 10:00:02 INFO  Successfully imported city: Mumbai with hashtag: #Mumbai
2026-01-22 10:00:03 INFO  Processing city 2/100: Delhi
2026-01-22 10:00:04 INFO  Successfully imported city: Delhi with hashtag: #Delhi
...
========== Import Job #1 Summary ==========
Status: COMPLETED
Started: 2026-01-22T10:00:01
Completed: 2026-01-22T10:03:25
Total cities: 100
Successfully imported: 95
Skipped (already exist): 3
Failed: 2
Failed cities (2):
  - Pimpri-Chinchwad (NO_DATA_FOUND): No polygon data returned from Nominatim API
  - Port Blair (FAILED): Connection timeout

To retry failed cities, use: POST /api/admin/localities/retry-failed-cities
============================================
```

## Retrying Failed Cities

After running the initial import, you can retry all failed cities with a single command:

### Command Line:
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
- Provides summary at the end
- Failed cities can be retried individually

## Database Schema

Cities are stored in the `localities` table with:
- `id` (auto-generated)
- `name` (city name, e.g., "Mumbai")
- `hashtag` (unique, e.g., "#Mumbai")
- `geo_boundary` (PostGIS Polygon with SRID 4326)

## Example Output

```
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
