# Locality Discovery & Import System

Two-phase system for discovering and importing Indian cities, towns, and districts from multiple data sources with full traceability and controlled imports.

## ✅ Implementation Status

**COMPLETED:**
- ✅ Database schema (4 entities, 5 repositories)
- ✅ Phase 1: Discovery services (GeoNames, OSM)
- ✅ Phase 1: Deduplication orchestrator
- ✅ Phase 2: Google Maps validation and import
- ✅ Admin REST API endpoints
- ✅ Consolidated integration tests
- ✅ All tests passing

**Services Implemented:**
1. `GeoNamesDiscoveryService` - Query GeoNames API for PPLA, PPLA2, ADM2
2. `OSMDiscoveryService` - Query OpenStreetMap Overpass API for admin boundaries
3. `LocalityDiscoveryOrchestrator` - Orchestrate multi-source discovery and deduplication
4. `LocalityImportService` - Google Maps validation with retry logic
5. `LocalityAdminController` - REST endpoints for discovery/import workflows

## Architecture

### Phase 1: Discovery (Automated, Heavily Logged)
1. Query GeoNames API (PPLA, PPLA2, PPLA3, ADM2 feature codes)
2. Query OpenStreetMap Overpass API (admin levels 5-8, place tags)
3. Query India Post (optional, graceful failure)
4. Save ALL raw discoveries to `raw_locality_discoveries` (unprocessed)
5. Deduplicate by normalized(name) + state → `discovered_localities`
6. Filter: Keep only CITY, TOWN, DISTRICT types
7. Confidence score = number of sources that found it
8. Full audit trail preserved in database

### Phase 2: Import (Manual, Controlled)
1. Select discovered localities by type (CITY/TOWN/DISTRICT)
2. Validate with Google Maps Geocoding API
3. Extract polygon from viewport/bounds
4. Save to `localities` table
5. Track import status with error handling
6. Supports resume and retry

## Database Schema

### locality_discovery_runs
Tracks each discovery run across all sources.

| Field | Type | Notes |
|-------|------|-------|
| id | BIGINT | PK |
| country_code | VARCHAR(10) | ISO code (e.g., "IN") |
| status | VARCHAR(20) | IN_PROGRESS, COMPLETED, FAILED, CANCELLED |
| started_at | TIMESTAMP | NOT NULL |
| completed_at | TIMESTAMP | NULL until done |
| total_raw_discoveries | INT | Across all sources |
| geonames_count | INT | Count from GeoNames |
| osm_count | INT | Count from OSM |
| indiapost_count | INT | Count from India Post |
| error_message | VARCHAR(1000) | If FAILED |

### raw_locality_discoveries
Raw data from each source before deduplication. One row per discovery per source.

| Field | Type | Notes |
|-------|------|-------|
| id | BIGINT | PK |
| discovery_run_id | BIGINT | FK to locality_discovery_runs |
| source | VARCHAR(20) | GEONAMES, OSM, INDIAPOST |
| name | VARCHAR(255) | As returned by source |
| state | VARCHAR(255) | NOT NULL |
| country_code | VARCHAR(10) | NOT NULL |
| locality_type | VARCHAR(20) | CITY, TOWN, DISTRICT, UNKNOWN |
| source_metadata | TEXT (JSON) | Source-specific data |
| created_at | TIMESTAMP | When discovered |

### discovered_localities
Deduplicated results with alternate names and confidence scores.

| Field | Type | Notes |
|-------|------|-------|
| id | BIGINT | PK |
| discovery_run_id | BIGINT | FK |
| official_name | VARCHAR(255) | Canonical name (first source) |
| alternate_names | JSON | Array of name variations |
| state | VARCHAR(255) | NOT NULL |
| country_code | VARCHAR(10) | NOT NULL |
| locality_type | VARCHAR(20) | CITY, TOWN, or DISTRICT only |
| sources | JSON | Array: ["geonames", "osm", "indiapost"] |
| confidence_score | INT | Number of sources (1-3) |
| created_at | TIMESTAMP | When deduplicated |

### import_jobs
Tracks each import job run for controlled imports.

| Field | Type | Notes |
|-------|------|-------|
| id | BIGINT | PK |
| discovery_run_id | BIGINT | FK |
| country_code | VARCHAR(10) | NOT NULL |
| status | VARCHAR(20) | IN_PROGRESS, COMPLETED, FAILED |
| started_at | TIMESTAMP | NOT NULL |
| completed_at | TIMESTAMP | NULL until done |
| total_localities | INT | To import |
| success_count | INT | Successfully imported |
| failure_count | INT | Failed attempts |
| skipped_count | INT | Already exist |
| filter_type | VARCHAR(20) | CITY, TOWN, DISTRICT, or ALL |

### locality_import_status
One row per locality import attempt with Google Maps validation.

| Field | Type | Notes |
|-------|------|-------|
| id | BIGINT | PK |
| import_job_id | BIGINT | FK |
| discovered_locality_id | BIGINT | FK |
| locality_name | VARCHAR(255) | NOT NULL |
| locality_type | VARCHAR(20) | CITY, TOWN, DISTRICT |
| state | VARCHAR(255) | NOT NULL |
| status | VARCHAR(20) | SUCCESS, FAILED, SKIPPED, NO_DATA_FOUND |
| error_message | TEXT | If status != SUCCESS |
| admin_level | INT | From Google Maps |
| google_maps_place_type | VARCHAR(100) | From Google Maps |
| attempt_count | INT | Number of retries |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | On retry |

## Entities

### LocalityDiscoveryRun
Metadata for discovery run. Status: IN_PROGRESS → COMPLETED (or FAILED/CANCELLED).
Tracks counts from each source for audit and debugging.

### RawLocalityDiscovery
Raw discovery from single source. Preserves source_metadata as JSON for traceability.
Enables seeing exactly what each source returned before deduplication.

### DiscoveredLocality
Deduplicated locality with:
- `official_name`: First source's name (priority: GeoNames > OSM > IndiaPost)
- `alternate_names`: All other variations found
- `confidence_score`: Number of sources (1-3)
- `sources`: Which sources found it
- Only CITY/TOWN/DISTRICT types; others filtered out

### ImportJob
Tracks controlled import run with counts and filter type.
Can be filtered by type (import cities first, then towns, then districts).

### LocalityImportStatus
Per-locality import attempt with:
- Google Maps validation results (admin_level, place_type)
- Error tracking with messages
- Retry support via attempt_count
- Status: SUCCESS/FAILED/SKIPPED/NO_DATA_FOUND

## REST API Endpoints

### Discovery Endpoints

**POST /admin/discovery/start**
- Start discovery run for a country
- Parameters: `countryCode` (e.g., "IN")
- Response: `LocalityDiscoveryRun` with IN_PROGRESS status

```bash
curl -X POST "http://localhost:8080/admin/discovery/start?countryCode=IN"
```

Response:
```json
{
  "id": 1,
  "countryCode": "IN",
  "status": "IN_PROGRESS",
  "startedAt": "2025-01-15T10:30:00Z",
  "geonamesCount": 0,
  "osmCount": 0,
  "indiapostCount": 0,
  "totalRawDiscoveries": 0
}
```

**GET /admin/discovery/status/{id}**
- Get discovery run status and progress
- Response: `LocalityDiscoveryRun` with current counts

```bash
curl "http://localhost:8080/admin/discovery/status/1"
```

Response:
```json
{
  "id": 1,
  "countryCode": "IN",
  "status": "COMPLETED",
  "startedAt": "2025-01-15T10:30:00Z",
  "completedAt": "2025-01-15T10:45:00Z",
  "geonamesCount": 150,
  "osmCount": 100,
  "indiapostCount": 52,
  "totalRawDiscoveries": 302
}
```

### Import Endpoints

**POST /admin/import/start**
- Start import job for discovered localities
- Parameters: `discoveryRunId`, `localityType` (CITY/TOWN/DISTRICT)
- Response: `ImportJob` with IN_PROGRESS status

```bash
curl -X POST "http://localhost:8080/admin/import/start?discoveryRunId=1&localityType=CITY"
```

Response:
```json
{
  "id": 1,
  "discoveryRunId": 1,
  "countryCode": "IN",
  "filterType": "CITY",
  "status": "IN_PROGRESS",
  "startedAt": "2025-01-15T10:46:00Z",
  "totalLocalities": 550,
  "successCount": 0,
  "failureCount": 0
}
```

**GET /admin/import/status/{id}**
- Get import job status and progress
- Response: `ImportJob` with success/failure counts

```bash
curl "http://localhost:8080/admin/import/status/1"
```

Response:
```json
{
  "id": 1,
  "discoveryRunId": 1,
  "countryCode": "IN",
  "filterType": "CITY",
  "status": "COMPLETED",
  "startedAt": "2025-01-15T10:46:00Z",
  "completedAt": "2025-01-15T11:15:00Z",
  "totalLocalities": 550,
  "successCount": 545,
  "failureCount": 5,
  "skippedCount": 0
}
```

## Workflow Example

Start the app
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```


### Step 1: Start Discovery
```bash
curl -X POST "http://localhost:8080/admin/discovery/start?countryCode=IN"
# Returns: LocalityDiscoveryRun (id=1, status=IN_PROGRESS)
```

### Step 2: Monitor Discovery Progress
```bash
curl "http://localhost:8080/admin/discovery/status/1"

# After completion:
{
  "id": 1,
  "status": "COMPLETED",
  "geonamesCount": 150,     # From GeoNames API
  "osmCount": 100,          # From OSM Overpass API
  "indiapostCount": 0,      # Optional source
  "totalRawDiscoveries": 250
}
```

**Behind the scenes:**
- GeoNames: Queries feature codes PPLA (cities), PPLA2 (towns), ADM2 (districts)
- OSM: Queries Overpass API for admin level boundaries
- Raw data saved to `raw_locality_discoveries` (allows debugging)
- Deduplication groups by normalized name + state → `discovered_localities`
- Result: ~1500-2000 unique localities across all types

### Step 3: Start Import for Cities
```bash
curl -X POST "http://localhost:8080/admin/import/start?discoveryRunId=1&localityType=CITY"
# Returns: ImportJob (id=1, status=IN_PROGRESS, totalLocalities=550)
```

### Step 4: Monitor Import Progress
```bash
curl "http://localhost:8080/admin/import/status/1"

# After completion:
{
  "id": 1,
  "discoveryRunId": 1,
  "filterType": "CITY",
  "status": "COMPLETED",
  "totalLocalities": 550,
  "successCount": 545,      # Validated with Google Maps
  "failureCount": 5         # API errors or no match
}
```

**Behind the scenes:**
- For each locality: Build query "Bengaluru, Karnataka, India"
- Call Google Maps Geocoding API
- Extract geometry (latitude/longitude)
- Save to `localities` table with status=SUCCESS
- On error: Set status=FAILED with error message
- Support retry with attemptCount tracking

### Step 5: Import Towns and Districts
```bash
# Repeat for TOWN and DISTRICT types
curl -X POST "http://localhost:8080/admin/import/start?discoveryRunId=1&localityType=TOWN"
curl -X POST "http://localhost:8080/admin/import/start?discoveryRunId=1&localityType=DISTRICT"
```

**Result:** ~1500-2000 localities in database with full audit trail

## Data Volumes

- **Raw Discoveries**: ~3000-5000 (includes duplicates)
- **Deduplicated**: ~1500-2000
  - Cities: ~500-800
  - Towns: ~200-400
  - Districts: ~700-750
- **Final (after Google Maps)**: ~1500-2000

## Cost Estimates

- **Discovery**: FREE
  - GeoNames: 30k credits/day free tier
  - OpenStreetMap: Free
- **Import**: ~$7.50
  - Google Geocoding: $0.005/request
  - 1500 × $0.005 = $7.50
  - Well within $200/month free tier

## Key Characteristics

1. **Full Traceability**: Every step tracked (raw → deduplicated → imported)
2. **Idempotent**: Re-run discovery safely, no duplicates
3. **Debuggable**: View raw data from each source
4. **Controlled**: Import by type, review before importing
5. **Resumable**: Crash-safe, continue from where stopped
6. **Cost-Effective**: Free discovery, minimal Google API calls
7. **Flexible**: Re-run discovery without re-importing, or vice versa
