# API Response Refactoring Plan

## Executive Summary

This document outlines a comprehensive refactoring strategy to standardize API responses across all endpoints in the **#local backend** application. The refactoring follows the pattern already implemented for the `AuthRefreshService` endpoint, replacing the monolithic `ResponseData` object with specific, typed response objects in the `model.response` package.

---

## Current State Analysis

### Old Pattern (Current Implementation)

**Problem:** Monolithic response structure using a god object (`ResponseData`)

```java
// Old Pattern Example - ProfileController
@GetMapping()
public ResponseEntity<APIResponse> getMyProfile(...) {
    ResponseData responseData = ResponseData.builder()
        .user(profile.get())
        .build();
    
    APIResponse response = APIResponse.builder()
        .data(responseData)
        .build();
    
    return ResponseEntity.ok(response);
}
```

**Issues with Current Approach:**
- `ResponseData` contains fields for ALL possible response types (issue, issues, mediaUrl, issueId, refreshToken, accessToken, user, etc.)
- Violates Single Responsibility Principle (SRP)
- Causes confusion about which fields are actually returned by each endpoint
- Makes API contracts unclear and difficult to document
- JSON responses include unnecessary null fields
- Difficult to maintain type safety across services and controllers

### New Pattern (Refactored - AuthRefreshService Example)

**Solution:** Specific typed response objects for each endpoint

```java
// New Pattern Example - AuthController
@PostMapping("/refresh")
public ResponseEntity<NewAPIResponse<AuthTokenResponseData>> refresh(
        @Valid @RequestBody AuthRefreshRequest request) {
    
    var tokenData = authRefreshService.refreshTokens(request.getRefreshToken());
    
    return ResponseEntity.ok(
        NewAPIResponse.<AuthTokenResponseData>builder()
            .data(tokenData)
            .build()
    );
}

// Service returns specific response object
public AuthTokenResponseData refreshTokens(String refreshToken) {
    return AuthTokenResponseData.builder()
        .accessToken(TokenResponse.builder()
            .value(newAccessToken)
            .expiry(newAccessTokenExpiry)
            .build())
        .refreshToken(TokenResponse.builder()
            .value(newRefreshToken)
            .expiry(newRefreshTokenExpiry)
            .build())
        .build();
}
```

**Benefits of New Approach:**
- ✅ Clear, type-safe contracts for each endpoint
- ✅ Only returns relevant data (no unnecessary null fields)
- ✅ Follows Single Responsibility Principle
- ✅ Easier to test with specific response objects
- ✅ Better IDE autocompletion and documentation
- ✅ Clearer separation of concerns

---

## Refactoring Strategy

### Step 1: Identify All API Endpoints

**Controllers to Refactor (9 total):**

1. **AuthController** (Partially Done)
   - ✅ `/auth/refresh` - DONE (using new pattern)
   - ⏳ `/auth/google/callback` - needs refactoring
   - ⏳ `/auth/google/token` - needs refactoring

2. **ProfileController**
   - ⏳ `GET /account/profile` - returns UserProfileModel
   - ⏳ Other profile-related endpoints

3. **IssueController**
   - ⏳ `GET /api/v1/issue/{issueId}` - returns single Issue
   - ⏳ `PATCH /api/v1/issue/{issueId}` - returns Issue
   - ⏳ Other issue endpoints

4. **IssueHomeV2Controller**
   - ⏳ Various endpoints returning issues

5. **IssueImportController**
   - ⏳ Import-related endpoints

6. **LocalityController**
   - ⏳ Locality-related endpoints

7. **LocalityAdminController**
   - ⏳ Admin operations

8. **MediaController**
   - ⏳ `POST /media/upload` - returns SignedUrlResponse
   - ⏳ Other media endpoints

9. **LocationMetadataController**
   - ⏳ Location metadata endpoints

---

### Step 2: Create Response Data Objects

The following response objects already exist or need to be created:

**Already Created (in `/model/response`):**
- ✅ `AuthTokenResponseData` - for token responses
- ✅ `UserProfileResponseData` - for user profile
- ✅ `IssueResponseData` - for single issue
- ✅ `IssueListResponseData` - for issue lists
- ✅ `IssueActionResponseData` - for issue actions
- ✅ `MediaUploadResponseData` - for media uploads

**Candidates for Creation:**
- `LocalityResponseData` - for locality operations
- `LocationMetadataResponseData` - for location metadata
- `IssueImportResponseData` - for import operations
- And others as needed

---

## Refactoring Process

### Phase 1: Response Layer (Highest Priority)

#### 1.1 Create Response Objects for Each Endpoint Type

```java
// Example structure for each response object

package org.smalltech.hashtaglocal_backend.model.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IssueActionResponseData {
    private Issue issue;
    private String actionStatus;  // or any other relevant fields
}
```

**Naming Convention:**
- Use specific, action-oriented names
- Follow pattern: `{Entity}{Action}ResponseData`
- Examples: `IssueCreateResponseData`, `LocalityUpdateResponseData`

#### 1.2 Organize Response Objects

```
src/main/java/org/smalltech/hashtaglocal_backend/model/response/
├── AuthTokenResponseData.java          ✅
├── UserProfileResponseData.java        ✅
├── IssueResponseData.java              ✅
├── IssueListResponseData.java          ✅
├── IssueActionResponseData.java        ✅
├── MediaUploadResponseData.java        ✅
├── LocalityResponseData.java
├── LocalityListResponseData.java
├── LocationMetadataResponseData.java
└── ... (add more as needed)
```

---

### Phase 2: Service Layer

#### 2.1 Update Service Method Signatures

**Before:**
```java
// Old: Returns the entity/model directly or within ResponseData
public Issue createIssue(IssueCreateRequest request) {
    // ... logic
    return issue;
}
```

**After:**
```java
// New: Returns specific response object
public IssueResponseData createIssue(IssueCreateRequest request) {
    // ... logic
    return IssueResponseData.builder()
        .issue(issue)
        .build();
}
```

#### 2.2 Update Service Methods Pattern

All services should follow this pattern:

```java
@Service
@Transactional
public class IssueService {
    
    public IssueResponseData getIssue(Long issueId) {
        IssueEntity entity = repository.findById(issueId).orElseThrow();
        Issue issue = mapToModel(entity);
        
        return IssueResponseData.builder()
            .issue(issue)
            .build();
    }
    
    public IssueListResponseData listIssues(Pageable pageable) {
        List<Issue> issues = repository.findAll(pageable)
            .stream()
            .map(this::mapToModel)
            .toList();
        
        return IssueListResponseData.builder()
            .issues(issues)
            .build();
    }
    
    public IssueActionResponseData createIssue(IssueCreateRequest request) {
        IssueEntity entity = new IssueEntity();
        // ... populate entity
        entity = repository.save(entity);
        
        return IssueActionResponseData.builder()
            .issue(mapToModel(entity))
            .actionStatus("created")
            .build();
    }
}
```

---

### Phase 3: Controller Layer

#### 3.1 Update Controller Method Signatures

**Before:**
```java
@GetMapping("/issue/{issueId}")
public APIResponse getIssue(@PathVariable Long issueId) {
    var issue = issueService.getIssue(issueId);
    return APIResponse.builder()
        .data(ResponseData.builder().issue(issue).build())
        .build();
}
```

**After:**
```java
@GetMapping("/issue/{issueId}")
public ResponseEntity<NewAPIResponse<IssueResponseData>> getIssue(
        @PathVariable Long issueId) {
    var issueData = issueService.getIssue(issueId);
    return ResponseEntity.ok(
        NewAPIResponse.<IssueResponseData>builder()
            .data(issueData)
            .build()
    );
}
```

#### 3.2 Controller Pattern

```java
@RestController
@RequestMapping("/api/v1")
public class IssueController {
    
    private final IssueService issueService;
    
    // GET single entity
    @GetMapping("/issue/{issueId}")
    public ResponseEntity<NewAPIResponse<IssueResponseData>> getIssue(
            @PathVariable Long issueId) {
        return ResponseEntity.ok(
            NewAPIResponse.<IssueResponseData>builder()
                .data(issueService.getIssue(issueId))
                .build()
        );
    }
    
    // GET list
    @GetMapping("/issues")
    public ResponseEntity<NewAPIResponse<IssueListResponseData>> listIssues(
            Pageable pageable) {
        return ResponseEntity.ok(
            NewAPIResponse.<IssueListResponseData>builder()
                .data(issueService.listIssues(pageable))
                .build()
        );
    }
    
    // POST create
    @PostMapping("/issue")
    public ResponseEntity<NewAPIResponse<IssueActionResponseData>> createIssue(
            @RequestBody @Valid IssueCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            NewAPIResponse.<IssueActionResponseData>builder()
                .data(issueService.createIssue(request))
                .build()
        );
    }
    
    // PATCH/PUT update
    @PatchMapping("/issue/{issueId}")
    public ResponseEntity<NewAPIResponse<IssueActionResponseData>> updateIssue(
            @PathVariable Long issueId,
            @RequestBody @Valid IssuePatchRequest request) {
        return ResponseEntity.ok(
            NewAPIResponse.<IssueActionResponseData>builder()
                .data(issueService.updateIssue(issueId, request))
                .build()
        );
    }
}
```

---

### Phase 4: Test Layer

#### 4.1 Update Service Tests

**Before:**
```java
@Test
void testGetIssue_Returns_IssueData() {
    Issue issue = issueService.getIssue(ISSUE_ID);
    assertNotNull(issue);
    assertEquals(EXPECTED_ID, issue.getId());
}
```

**After:**
```java
@Test
void testGetIssue_Returns_IssueResponseData() {
    IssueResponseData responseData = issueService.getIssue(ISSUE_ID);
    
    assertNotNull(responseData);
    assertNotNull(responseData.getIssue());
    assertEquals(EXPECTED_ID, responseData.getIssue().getId());
}
```

#### 4.2 Update Controller Integration Tests

**Pattern:**
```java
@ExtendWith(MockitoExtension.class)
class IssueControllerTest {
    
    @Mock
    private IssueService issueService;
    
    @InjectMocks
    private IssueController issueController;
    
    @Test
    void testGetIssue_ReturnsIssueResponseData() {
        // Arrange
        IssueResponseData mockResponse = IssueResponseData.builder()
            .issue(Issue.builder().id(1L).build())
            .build();
        
        when(issueService.getIssue(1L))
            .thenReturn(mockResponse);
        
        // Act
        ResponseEntity<NewAPIResponse<IssueResponseData>> response = 
            issueController.getIssue(1L);
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getData().getIssue().getId());
        
        verify(issueService).getIssue(1L);
    }
}
```

---

## Implementation Roadmap

### Priority Levels

**HIGH Priority (Start First):**
1. `AuthController` - `/auth/google/callback` & `/auth/google/token`
2. `ProfileController` - User profile endpoints
3. `IssueController` - Core issue operations
4. `MediaController` - Media upload/handling
5. `IssueHomeV2Controller` - Home feed endpoints

**LOW Priority:**
6. `LocalityController` - Locality operations
7. `LocationMetadataController` - Metadata endpoints
8. `LocalityAdminController` - Admin operations
9. `IssueImportController` - Batch import operations

---

## Refactoring Checklist

For each endpoint, follow this checklist:

- [ ] **Response Object Created**
  - [ ] File created in `model.response` package
  - [ ] Proper annotations (@Data, @Builder)
  - [ ] Correct field types and naming

- [ ] **Service Updated**
  - [ ] Method return type changed to response data object
  - [ ] Response object properly constructed
  - [ ] Tests updated for service tests

- [ ] **Controller Updated**
  - [ ] Import statement added for response object
  - [ ] Return type changed to `ResponseEntity<NewAPIResponse<T>>`
  - [ ] Response construction updated

- [ ] **Tests Updated**
  - [ ] Service tests updated for new return type
  - [ ] Controller tests (if present) updated
  - [ ] Integration tests validated
  - [ ] All tests passing

- [ ] **Documentation Updated**
  - [ ] Swagger/OpenAPI annotations updated if needed
  - [ ] Method comments reflect new response type

---

## Code Examples by Endpoint Type

### Example 1: Single Entity Retrieval (GET)

**Service:**
```java
public IssueResponseData getIssue(Long issueId) {
    IssueEntity entity = issueRepository.findById(issueId)
        .orElseThrow(() -> new EntityNotFoundException("Issue not found"));
    
    Issue issue = issueMapper.map(entity);
    
    return IssueResponseData.builder()
        .issue(issue)
        .build();
}
```

**Controller:**
```java
@GetMapping("/issue/{issueId}")
@Operation(summary = "Get issue by ID")
public ResponseEntity<NewAPIResponse<IssueResponseData>> getIssue(
        @PathVariable Long issueId) {
    var responseData = issueService.getIssue(issueId);
    return ResponseEntity.ok(
        NewAPIResponse.<IssueResponseData>builder()
            .data(responseData)
            .build()
    );
}
```

**Test:**
```java
@Test
void testGetIssue_WithValidId_ReturnsIssueResponseData() {
    // Arrange
    Long issueId = 1L;
    IssueEntity entity = createTestIssueEntity(issueId);
    IssueResponseData expected = IssueResponseData.builder()
        .issue(issueMapper.map(entity))
        .build();
    
    when(issueRepository.findById(issueId))
        .thenReturn(Optional.of(entity));
    
    // Act
    IssueResponseData response = issueService.getIssue(issueId);
    
    // Assert
    assertNotNull(response);
    assertEquals(expected.getIssue().getId(), response.getIssue().getId());
}
```

---

### Example 2: List with Pagination (GET)

**Response Object:**
```java
@Data
@Builder
public class IssueListResponseData {
    private List<Issue> issues;
    private Integer totalCount;
    private Integer pageNumber;
    private Integer pageSize;
}
```

**Service:**
```java
public IssueListResponseData listIssues(int page, int size) {
    Pageable pageable = PageRequest.of(page, size);
    Page<IssueEntity> entities = issueRepository.findAll(pageable);
    
    List<Issue> issues = entities.stream()
        .map(issueMapper::map)
        .toList();
    
    return IssueListResponseData.builder()
        .issues(issues)
        .totalCount((int) entities.getTotalElements())
        .pageNumber(page)
        .pageSize(size)
        .build();
}
```

**Controller:**
```java
@GetMapping("/issues")
public ResponseEntity<NewAPIResponse<IssueListResponseData>> listIssues(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {
    var responseData = issueService.listIssues(page, size);
    return ResponseEntity.ok(
        NewAPIResponse.<IssueListResponseData>builder()
            .data(responseData)
            .build()
    );
}
```

---

### Example 3: Create Operation (POST)

**Request Object:**
```java
@Data
@Builder
@ValidatedAnnotationIfNeeded
public class IssueCreateRequest {
    @NotBlank(message = "Title is required")
    private String title;
    
    @NotNull(message = "Latitude is required")
    private Double latitude;
    
    @NotNull(message = "Longitude is required")
    private Double longitude;
}
```

**Response Object:**
```java
@Data
@Builder
public class IssueActionResponseData {
    private Issue issue;
    private String message;
    private Long createdAt;
}
```

**Service:**
```java
public IssueActionResponseData createIssue(IssueCreateRequest request) {
    IssueEntity entity = new IssueEntity();
    entity.setTitle(request.getTitle());
    entity.setLatitude(request.getLatitude());
    entity.setLongitude(request.getLongitude());
    entity.setCreatedAt(Instant.now().getEpochSecond());
    
    entity = issueRepository.save(entity);
    Issue issue = issueMapper.map(entity);
    
    return IssueActionResponseData.builder()
        .issue(issue)
        .message("Issue created successfully")
        .createdAt(entity.getCreatedAt())
        .build();
}
```

**Controller:**
```java
@PostMapping("/issue")
@Operation(summary = "Create new issue")
public ResponseEntity<NewAPIResponse<IssueActionResponseData>> createIssue(
        @RequestBody @Valid IssueCreateRequest request) {
    var responseData = issueService.createIssue(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(
        NewAPIResponse.<IssueActionResponseData>builder()
            .data(responseData)
            .build()
    );
}
```

---

### Example 4: Update Operation (PATCH/PUT)

**Request Object:**
```java
@Data
@Builder
public class IssuePatchRequest {
    @NotBlank
    private String status;
    
    private String description;
    
    private String type;
}
```

**Service:**
```java
public IssueActionResponseData updateIssue(Long issueId, IssuePatchRequest request) {
    IssueEntity entity = issueRepository.findById(issueId)
        .orElseThrow(() -> new EntityNotFoundException("Issue not found"));
    
    if (request.getStatus() != null) {
        entity.setStatus(request.getStatus());
    }
    if (request.getDescription() != null) {
        entity.setDescription(request.getDescription());
    }
    if (request.getType() != null) {
        entity.setType(request.getType());
    }
    
    entity.setUpdatedAt(Instant.now().getEpochSecond());
    entity = issueRepository.save(entity);
    
    return IssueActionResponseData.builder()
        .issue(issueMapper.map(entity))
        .message("Issue updated successfully")
        .build();
}
```

**Controller:**
```java
@PatchMapping("/issue/{issueId}")
public ResponseEntity<NewAPIResponse<IssueActionResponseData>> updateIssue(
        @PathVariable Long issueId,
        @RequestBody @Valid IssuePatchRequest request) {
    var responseData = issueService.updateIssue(issueId, request);
    return ResponseEntity.ok(
        NewAPIResponse.<IssueActionResponseData>builder()
            .data(responseData)
            .build()
    );
}
```

---

## Benefits Summary

### For Developers
- 🎯 Clear type safety from IDE autocompletion
- 🧹 Cleaner, more readable code
- 🐛 Easier debugging with specific objects
- 📚 Self-documenting API contracts

### For Operations
- 📊 Reduced JSON payload sizes (no unnecessary null fields)
- 🚀 Better performance (smaller responses)
- 🔒 More secure (only returns needed data)

### For Testing
- ✅ Easier to write unit tests
- ✅ Clearer test assertions
- ✅ Better test maintainability
- ✅ Reduced mock complexity

### For API Consumers
- 📖 Clear, predictable response structures
- 🎓 Better API documentation
- 🔄 Easier to parse responses
- 🛡️ Type-safe client generation

---

## Migration Order for Maximum Impact

1. **Start with Authentication APIs** ✅ (Already done for refresh)
   - Create `GoogleAuthResponseData` for callback and token endpoints

2. **Profile APIs** (High usage, high visibility)
   - Create/refactor `UserProfileResponseData`
   - Create `ProfileUpdateResponseData`

3. **Issue APIs** (Core functionality)
   - `IssueResponseData` (exists, update usage)
   - `IssueListResponseData` (exists, update usage)
   - `IssueActionResponseData` (exists, update usage)

4. **Media APIs**
   - `MediaUploadResponseData` (exists, update usage)

5. **Locality & Location APIs**
   - Create `LocalityResponseData`, `LocalityListResponseData`
   - Create `LocationMetadataResponseData`

6. **Admin & Import APIs**
   - Create specific response objects for admin operations
   - Create `IssueImportResponseData` for batch operations

---

## Conclusions

This refactoring will significantly improve code quality, maintainability, and API clarity. The pattern is already validated with the `AuthRefreshService` endpoint. Begin with high-priority endpoints and systematically work through all controllers, ensuring each endpoint has a corresponding response object in the `model.response` package.

The refactoring follows SOLID principles and establishes a consistent, scalable pattern for future API development.
