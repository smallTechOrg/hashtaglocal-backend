# Location Metadata Update - Quick Start Guide

## Prerequisites

1. **Google Maps API Key**: You need a valid Google Maps Geocoding API key
2. **Database**: PostgreSQL with PostGIS extension installed
3. **Environment**: The application should be running

## Setup

### 1. Configure API Key

Add your Google Maps API key to your environment:

```bash
export GOOGLE_MAPS_API_KEY=your-actual-api-key-here
```

Or update `.env` file:
```
GOOGLE_MAPS_API_KEY=your-actual-api-key-here
```

### 2. Verify Configuration

The API key is configured in `application.yaml`:
```yaml
google:
  maps:
    api-key: ${GOOGLE_MAPS_API_KEY:}
```

## Running the Job

### Option 1: Using cURL

**Update all locations:**
```bash
curl -X POST "http://localhost:8080/api/v1/admin/location-metadata/update"
```

**Update only locations with missing metadata:**
```bash
curl -X POST "http://localhost:8080/api/v1/admin/location-metadata/update?partial=true"
```

### Option 2: Using Swagger UI

1. Navigate to: `http://localhost:8080/v1/swagger-ui.html`
2. Find the "Location Metadata" section
3. Click on "POST /api/v1/admin/location-metadata/update"
4. Click "Try it out"
5. Set `partial` parameter (true/false)
6. Click "Execute"

### Option 3: Using Postman

```
POST http://localhost:8080/api/v1/admin/location-metadata/update?partial=false
```

## Expected Output

### Success Response
```json
{
  "totalProcessed": 150,
  "successCount": 148,
  "failureCount": 2,
  "skippedCount": 0
}
```

### Console Logs
```
🚀 Starting location metadata update job...
📊 Found 150 total locations to process
✅ Updated location ID=1: name='C 320 Golden Corner', city='Bengaluru'
✅ Updated location ID=2: name='MG Road', city='Bengaluru'
📈 Progress: 100/150 locations processed
🎉 Location metadata update job completed: Total=150, Success=148, Failed=2, Skipped=0
```

## Verify Results

### Check Database

```sql
-- View updated location metadata
SELECT 
    id,
    name,
    meta_data->>'city' as city,
    meta_data->>'region' as region,
    meta_data->>'formatted_address' as address
FROM locations
LIMIT 10;
```

### Example Result
```
id | name                              | city       | region     | address
---+-----------------------------------+------------+------------+------------------------------------------
1  | C 320 Golden Corner, Bellandur... | Bengaluru  | Karnataka  | Sarjapur - Marathahalli Road, Bengaluru...
2  | MG Road                           | Bengaluru  | Karnataka  | MG Road, Bengaluru, Karnataka
```

## Metadata Structure

Each location will have metadata in this format:

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

## Performance Considerations

- **Rate Limiting**: The job processes ~10 requests/second to stay within Google Maps API limits
- **Processing Time**: ~100 locations will take approximately 10 seconds
- **API Quota**: Google Maps has daily quotas - monitor your usage in Google Cloud Console

## Troubleshooting

### Problem: "No metadata returned"
- Check that Google Maps API key is correctly set
- Verify the API key has Geocoding API enabled
- Check billing is enabled in Google Cloud Console

### Problem: "Rate limit exceeded"
- Increase `DELAY_BETWEEN_REQUESTS_MS` in LocationMetadataUpdateJob
- Run partial updates instead of full updates

### Problem: Incomplete metadata
- Some addresses might not have all fields (e.g., postal code)
- This is normal - Google Maps returns what's available
- The job handles missing fields gracefully

## Monitoring API Usage

Check your Google Maps API usage:
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Navigate to "APIs & Services" > "Dashboard"
3. Click on "Geocoding API"
4. View usage metrics

## Cost Estimation

Google Maps Geocoding API pricing (as of 2024):
- $5 per 1,000 requests
- First $200/month is free (40,000 requests)

For 1,000 locations:
- Cost: ~$5
- Time: ~100 seconds

## Best Practices

1. **Test First**: Run with `partial=true` on a few locations
2. **Monitor Logs**: Watch the console for errors
3. **Check Results**: Verify a sample of updated locations
4. **Schedule Off-Peak**: Run during low-traffic periods
5. **Backup Data**: Consider backing up before bulk updates

## Scheduling (Future Enhancement)

To run automatically, you could add a scheduled job:

```java
@Scheduled(cron = "0 0 2 * * ?") // 2 AM daily
public void scheduledMetadataUpdate() {
    locationMetadataUpdateJob.runPartialUpdate();
}
```

## Support

For issues or questions:
- Check logs in console
- Review LOCATION_METADATA_UPDATE.md for detailed documentation
- Verify Google Maps API configuration
