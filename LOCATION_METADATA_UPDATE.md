# Location Metadata Update Feature

## Overview

This feature provides automated updating of location metadata and names using Google Maps Geocoding API. The system performs reverse geocoding on location coordinates to extract detailed, structured metadata.

## Components

### 1. DTOs

#### LocationMetadataDTO
Structured representation of location metadata with the following fields:
- `city`: City name
- `name`: Location name (extracted from address)
- `region`: Administrative region (state/province)
- `street`: Street name
- `country`: Country name
- `district`: District/sublocality
- `timezone`: Timezone (null - not provided by Geocoding API)
- `sub_region`: Secondary administrative region
- `postal_code`: Postal/ZIP code
- `street_number`: Street number
- `iso_country_code`: ISO country code (e.g., "IN")
- `formatted_address`: Concise formatted address

#### GoogleMapsLocationDTO
Extended to include `addressComponents` for parsing detailed location data from Google Maps API response.

### 2. Services

#### GoogleMapsGeocodingService
Core service for interacting with Google Maps Geocoding API:
- **`reverseGeocode(latitude, longitude)`**: Performs reverse geocoding
- **`metadataToMap(metadata)`**: Converts DTO to Map for JSONB storage
- **`extractMetadata(result)`**: Parses Google Maps response into structured metadata
- **`parseAddressComponents(components)`**: Extracts address components
- **`extractLocationName(components)`**: Generates meaningful location name
- **`buildFormattedAddress(components, fullAddress)`**: Creates concise formatted address

### 3. Job

#### LocationMetadataUpdateJob
Batch processing job to update location metadata:
- **`runFullUpdate()`**: Updates all locations
- **`runPartialUpdate()`**: Updates only locations with missing/incomplete metadata
- Includes rate limiting (100ms delay between requests)
- Batch size: 100 locations
- Returns `LocationUpdateJobResult` with success/failure counts

### 4. Controller

#### LocationMetadataController
REST endpoint for triggering metadata updates:
- **POST** `/api/v1/admin/location-metadata/update`
  - Query param: `partial` (default: false)
  - `partial=true`: Updates only locations with missing metadata
  - `partial=false`: Updates all locations

## Usage

### API Endpoint

```bash
# Update all locations
curl -X POST "http://localhost:8080/api/v1/admin/location-metadata/update"

# Update only locations with missing metadata
curl -X POST "http://localhost:8080/api/v1/admin/location-metadata/update?partial=true"
```

### Response Example

```json
{
  "totalProcessed": 150,
  "successCount": 145,
  "failureCount": 5,
  "skippedCount": 0
}
```

### Metadata Example

After processing, location records will have metadata in this format:

```json
{
  "city": "Bengaluru",
  "name": "C 320 Golden Corner, Bellandur Gate",
  "region": "Karnataka",
  "street": "Sarjapur - Marathahalli Road",
  "country": "India",
  "district": "Bellandur",
  "timezone": null,
  "sub_region": "Bangalore Division",
  "postal_code": "560103",
  "street_number": null,
  "iso_country_code": "IN",
  "formatted_address": "Sarjapur - Marathahalli Road, Bengaluru, Karnataka"
}
```

## Configuration

Ensure the Google Maps API key is configured in `application.yaml`:

```yaml
google:
  maps:
    api-key: ${GOOGLE_MAPS_API_KEY:}
```

Set the environment variable:
```bash
export GOOGLE_MAPS_API_KEY=your-api-key-here
```

## Rate Limiting

The job includes rate limiting to comply with Google Maps API quotas:
- Default delay: 100ms between requests (~10 requests/second)
- Adjustable via `DELAY_BETWEEN_REQUESTS_MS` constant
- Google Maps standard limit: ~50 requests/second

## Monitoring

The job logs detailed progress:
- Individual location updates (debug level)
- Batch progress every 100 locations
- Success/failure counts
- Warnings for locations without metadata
- Final job summary

## Error Handling

- Failed requests are logged but don't stop the job
- Null/empty responses are handled gracefully
- Network errors are caught and logged
- Transaction rollback on critical failures

## Testing

You can test the feature using Swagger UI at:
```
http://localhost:8080/v1/swagger-ui.html
```

Navigate to "Location Metadata" section and try the update endpoint.

## Architecture

```
Controller (LocationMetadataController)
    ↓
Job (LocationMetadataUpdateJob)
    ↓
Service (GoogleMapsGeocodingService)
    ↓
Google Maps Geocoding API
    ↓
LocationRepository (saves updated data)
```

## Future Enhancements

1. Add timezone support using Google Maps Time Zone API
2. Implement async job processing with status tracking
3. Add scheduled job support (cron)
4. Implement retry logic for failed locations
5. Add job history and audit logging
6. Support for custom metadata field mapping
