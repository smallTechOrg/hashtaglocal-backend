# High Priority API Response Refactoring Plan

## Overview

This document focuses on refactoring **5 HIGH PRIORITY controllers** following the pattern established by `AuthRefreshService`. These controllers handle the most critical operations and will provide the greatest impact when refactored.

### High Priority Controllers
1. **AuthController** - Authentication & token refresh
2. **ProfileController** - User profile management
3. **IssueController** - Core issue operations
4. **MediaController** - Media handling & uploads
5. **IssueHomeV2Controller** - Home feed operations

---

## 1. AuthController Refactoring

### Current State

```java
@RestController
@RequestMapping("/auth")
public class AuthController {
    
    @GetMapping("/google/callback")
    public ResponseEntity<APIResponse> googleCallback(@RequestParam("code") String code, ...) {
        return ResponseEntity.ok(googleAuthService.handleAuthorizationCode(code, codeVerifier));
    }

    @GetMapping("/google/token")
    public ResponseEntity<APIResponse> authenticateWithAccessToken(@RequestParam("access_token") String accessToken) {
        return ResponseEntity.ok(googleAuthService.handleAccessToken(accessToken));
    }

    @PostMapping("/refresh")  // ✅ ALREADY REFACTORED
    public ResponseEntity<NewAPIResponse<AuthTokenResponseData>> refresh(...) {
        var tokenData = authRefreshService.refreshTokens(request.getRefreshToken());
        return ResponseEntity.ok(NewAPIResponse.<AuthTokenResponseData>builder().data(tokenData).build());
    }
}
```

### Issues to Fix
- ❌ `/google/callback` returns old `APIResponse` format
- ❌ `/google/token` returns old `APIResponse` format
- ✅ `/refresh` is already refactored (use as template)

### Solution: Response Objects

**Create new response object:**

```java
// File: src/main/java/org/smalltech/hashtaglocal_backend/model/response/GoogleAuthResponseData.java
package org.smalltech.hashtaglocal_backend.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.smalltech.hashtaglocal_backend.model.TokenResponse;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleAuthResponseData {
    private TokenResponse accessToken;
    private TokenResponse refreshToken;
    private String userId;           // or User object if needed
    private Boolean isNewUser;       // useful to indicate first-time login
}
```

### Service Updates

**GoogleAuthService - handleAuthorizationCode():**

```java
// Before:
public APIResponse handleAuthorizationCode(String code, String codeVerifier) {
    // ... logic ...
    return APIResponse.builder().data(ResponseData.builder()
        .accessToken(TokenResponse.builder()...build())
        .refreshToken(TokenResponse.builder()...build())
        .build()).build();
}

// After:
public GoogleAuthResponseData handleAuthorizationCode(String code, String codeVerifier) {
    // ... existing logic to generate tokens ...
    
    return GoogleAuthResponseData.builder()
        .accessToken(TokenResponse.builder()
            .value(accessToken)
            .expiry(accessTokenExpiry)
            .build())
        .refreshToken(TokenResponse.builder()
            .value(refreshToken)
            .expiry(refreshTokenExpiry)
            .build())
        .userId(user.getId().toString())
        .isNewUser(isNewUser)
        .build();
}
```

**GoogleAuthService - handleAccessToken():**

```java
// Before:
public APIResponse handleAccessToken(String accessToken) {
    // ... logic ...
    return APIResponse.builder().data(ResponseData.builder()...build()).build();
}

// After:
public GoogleAuthResponseData handleAccessToken(String accessToken) {
    // ... existing logic ...
    
    return GoogleAuthResponseData.builder()
        .accessToken(TokenResponse.builder()
            .value(newAccessToken)
            .expiry(newAccessTokenExpiry)
            .build())
        .refreshToken(TokenResponse.builder()
            .value(newRefreshToken)
            .expiry(newRefreshTokenExpiry)
            .build())
        .userId(user.getId().toString())
        .isNewUser(false)
        .build();
}
```

### Controller Updates

```java
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Google OAuth APIs")
public class AuthController {

    private final AuthRefreshService authRefreshService;
    private final GoogleAuthService googleAuthService;

    public AuthController(AuthRefreshService authRefreshService, GoogleAuthService googleAuthService) {
        this.authRefreshService = authRefreshService;
        this.googleAuthService = googleAuthService;
    }

    @GetMapping("/google/callback")
    @Operation(summary = "Google OAuth callback")
    public ResponseEntity<NewAPIResponse<GoogleAuthResponseData>> googleCallback(
            @RequestParam("code") String code,
            @RequestParam(value = "code_verifier", required = false) String codeVerifier) {

        System.out.println("➡️ /auth/google/callback hit");
        var authData = googleAuthService.handleAuthorizationCode(code, codeVerifier);
        
        return ResponseEntity.ok(
            NewAPIResponse.<GoogleAuthResponseData>builder()
                .data(authData)
                .build()
        );
    }

    @GetMapping("/google/token")
    @Operation(summary = "Authenticate using Google access token")
    public ResponseEntity<NewAPIResponse<GoogleAuthResponseData>> authenticateWithAccessToken(
            @RequestParam("access_token") String accessToken) {

        System.out.println("➡️ /auth/google/token hit");
        var authData = googleAuthService.handleAccessToken(accessToken);
        
        return ResponseEntity.ok(
            NewAPIResponse.<GoogleAuthResponseData>builder()
                .data(authData)
                .build()
        );
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access and refresh tokens")
    public ResponseEntity<NewAPIResponse<AuthTokenResponseData>> refresh(
            @Valid @RequestBody AuthRefreshRequest request) {

        var tokenData = authRefreshService.refreshTokens(request.getRefreshToken());
        
        return ResponseEntity.ok(
            NewAPIResponse.<AuthTokenResponseData>builder()
                .data(tokenData)
                .build()
        );
    }
}
```

### Test Updates (AuthRefreshServiceTest as reference)

```java
@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private UserAuthSessionRepository userAuthSessionRepository;
    
    @Mock
    private TokenService tokenService;

    private GoogleAuthService googleAuthService;

    @Test
    void testHandleAuthorizationCode_WithValidCode_ReturnsGoogleAuthResponseData() {
        // Arrange
        String code = "valid-auth-code";
        UserEntity user = UserEntity.builder()
            .id(1L)
            .email("user@example.com")
            .build();
        
        when(googleAuthService.handleAuthorizationCode(code, null))
            .thenReturn(GoogleAuthResponseData.builder()
                .accessToken(TokenResponse.builder()
                    .value("new-access-token")
                    .expiry(3000L)
                    .build())
                .refreshToken(TokenResponse.builder()
                    .value("new-refresh-token")
                    .expiry(4000L)
                    .build())
                .userId("1")
                .isNewUser(true)
                .build());

        // Act
        GoogleAuthResponseData response = googleAuthService.handleAuthorizationCode(code, null);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals("1", response.getUserId());
        assertTrue(response.getIsNewUser());
        
        verify(userRepository).save(any(UserEntity.class));
    }
}
```

---

## 2. ProfileController Refactoring

### Current State

```java
@RestController
@RequestMapping("/account/profile")
public class ProfileController {
    
    @GetMapping()
    public ResponseEntity<APIResponse> getMyProfile(
            @RequestHeader(value = "Authorization") String authorization,
            @RequestParam(value = "lat", required = false) Double latitude,
            @RequestParam(value = "lng", required = false) Double longitude) {
        
        String accessToken = extractBearerToken(authorization);
        Optional<UserProfileModel> profile = profileService.getMyProfile(accessToken, latitude, longitude);
        
        ResponseData responseData = ResponseData.builder().user(profile.get()).build();
        APIResponse response = APIResponse.builder().data(responseData).build();
        
        return ResponseEntity.ok(response);
    }
}
```

### Issues to Fix
- ❌ Returns old `APIResponse` with `ResponseData`
- ❌ Doesn't use `UserProfileResponseData` that already exists
- ❌ Service doesn't return typed response object

### Solution: Use Existing Response Object

**Response Object (already exists):**

```java
// File: src/main/java/org/smalltech/hashtaglocal_backend/model/response/UserProfileResponseData.java
package org.smalltech.hashtaglocal_backend.model.response;

import lombok.Builder;
import lombok.Data;
import org.smalltech.hashtaglocal_backend.model.UserProfileModel;

@Data
@Builder
public class UserProfileResponseData {
    private UserProfileModel user;
}
```

### Service Updates

```java
@Service
public class GetProfileService {
    
    // Before:
    public Optional<UserProfileModel> getMyProfile(String accessToken, Double latitude, Double longitude) {
        // ... logic ...
        return Optional.of(userProfileModel);
    }

    // After:
    public UserProfileResponseData getMyProfile(String accessToken, Double latitude, Double longitude) {
        UserProfileModel profile = // ... existing logic to fetch and build profile ...
        
        return UserProfileResponseData.builder()
            .user(profile)
            .build();
    }
}
```

### Controller Updates

```java
@RestController
@RequestMapping("/account/profile")
public class ProfileController {

    private final GetProfileService profileService;

    public ProfileController(GetProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Get authenticated user's own profile
     */
    @GetMapping()
    public ResponseEntity<NewAPIResponse<UserProfileResponseData>> getMyProfile(
            @RequestHeader(value = "Authorization") String authorization,
            @RequestParam(value = "lat", required = false) Double latitude,
            @RequestParam(value = "lng", required = false) Double longitude) {

        System.out.println("➡️ /account/profile called");
        
        String accessToken = extractBearerToken(authorization);

        if (accessToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(NewAPIResponse.<UserProfileResponseData>builder().data(null).build());
        }

        UserProfileResponseData profileData = profileService.getMyProfile(accessToken, latitude, longitude);

        if (profileData == null || profileData.getUser() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(NewAPIResponse.<UserProfileResponseData>builder().data(null).build());
        }

        return ResponseEntity.ok(
            NewAPIResponse.<UserProfileResponseData>builder()
                .data(profileData)
                .build()
        );
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
```

### Test Updates

```java
@ExtendWith(MockitoExtension.class)
class GetProfileServiceTest {

    @Mock
    private UserAuthSessionRepository userAuthSessionRepository;

    private GetProfileService getProfileService;

    @Test
    void testGetMyProfile_WithValidToken_ReturnsUserProfileResponseData() {
        // Arrange
        String accessToken = "valid-token";
        UserProfileModel expectedProfile = UserProfileModel.builder()
            .id(1L)
            .email("user@example.com")
            .build();

        when(userAuthSessionRepository.findByAccessToken(accessToken))
            .thenReturn(Optional.of(createTestSession(accessToken)));

        // Act
        UserProfileResponseData response = getProfileService.getMyProfile(accessToken, null, null);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getUser());
        assertEquals("user@example.com", response.getUser().getEmail());
        
        verify(userAuthSessionRepository).findByAccessToken(accessToken);
    }

    private UserAuthSessionEntity createTestSession(String accessToken) {
        return UserAuthSessionEntity.builder()
            .id(1L)
            .accessToken(accessToken)
            .build();
    }
}
```

---

## 3. IssueController Refactoring

### Current State

```java
@RestController
@RequestMapping("/api/v1")
public class IssueController {

    @GetMapping("/issue/{issueId}")
    public APIResponse getIssue(@PathVariable Long issueId) {
        var issueEntity = issueQueryService.get(issueId);
        var issue = issueViewMapper.map(issueEntity);
        return APIResponse.builder().data(ResponseData.builder().issue(issue).build()).build();
    }

    @PatchMapping("/issue/{issueId}")
    public ResponseEntity<APIResponse> patchIssue(@PathVariable Long issueId, ...) {
        var result = issuePatchService.patch(issueId, request);
        return ResponseEntity.ok(
            APIResponse.builder().data(ResponseData.builder().issue(result).build()).build()
        );
    }
    
    // Other endpoints...
}
```

### Issues to Fix
- ❌ Multiple endpoints return old `APIResponse` format
- ❌ Response objects `IssueResponseData`, `IssueListResponseData`, `IssueActionResponseData` exist but aren't being used
- ❌ Services return raw Issue objects, not response objects

### Solution: Use Existing Response Objects

**Response Objects (already exist, but need usage updates):**

```java
// IssueResponseData - for single issue GET operations
@Data
@Builder
public class IssueResponseData {
    private Issue issue;
}

// IssueListResponseData - for list operations
@Data
@Builder
public class IssueListResponseData {
    private List<Issue> issues;
}

// IssueActionResponseData - for create/update/delete operations
@Data
@Builder
public class IssueActionResponseData {
    private Issue issue;
}
```

### Service Updates

**Example - IssueQueryService:**

```java
@Service
public class IssueQueryService {
    
    // Get single issue
    public IssueResponseData get(Long issueId) {
        IssueEntity entity = issueRepository.findById(issueId)
            .orElseThrow(() -> new EntityNotFoundException("Issue not found"));
        
        Issue issue = issueViewMapper.map(entity);
        
        return IssueResponseData.builder()
            .issue(issue)
            .build();
    }
    
    // Get all issues (with pagination)
    public IssueListResponseData getAllIssues(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<IssueEntity> entities = issueRepository.findAll(pageable);
        
        List<Issue> issues = entities.stream()
            .map(issueViewMapper::map)
            .toList();
        
        return IssueListResponseData.builder()
            .issues(issues)
            .build();
    }
}

// IssuePatchService
@Service
@Transactional
public class IssuePatchService {
    
    public IssueActionResponseData patch(Long issueId, IssuePatchRequest request) {
        IssueEntity entity = issueRepository.findById(issueId)
            .orElseThrow(() -> new EntityNotFoundException("Issue not found"));
        
        // Apply patches from request
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        // ... other updates ...
        
        entity = issueRepository.save(entity);
        Issue issue = issueViewMapper.map(entity);
        
        return IssueActionResponseData.builder()
            .issue(issue)
            .build();
    }
}

// IssueActionService (for create, report, verify)
@Service
@Transactional
public class IssueActionService {
    
    public IssueActionResponseData createIssue(IssueCreateRequest request) {
        IssueEntity entity = new IssueEntity();
        // ... populate entity from request ...
        entity = issueRepository.save(entity);
        
        return IssueActionResponseData.builder()
            .issue(issueViewMapper.map(entity))
            .build();
    }
    
    public IssueActionResponseData reportIssue(Long issueId, IssueReportRequest request) {
        IssueEntity entity = issueRepository.findById(issueId).orElseThrow();
        // ... handle report ...
        entity = issueRepository.save(entity);
        
        return IssueActionResponseData.builder()
            .issue(issueViewMapper.map(entity))
            .build();
    }
}
```

### Controller Updates

```java
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Issue", description = "issue APIs")
@RequiredArgsConstructor
public class IssueController {

    private final IssueActionService issueActionService;
    private final IssuePatchService issuePatchService;
    private final IssueQueryService issueQueryService;

    @GetMapping("/issue/{issueId}")
    @Operation(summary = "Get issue by ID")
    public ResponseEntity<NewAPIResponse<IssueResponseData>> getIssue(
            @PathVariable Long issueId) {
        
        var issueData = issueQueryService.get(issueId);
        
        return ResponseEntity.ok(
            NewAPIResponse.<IssueResponseData>builder()
                .data(issueData)
                .build()
        );
    }

    @GetMapping("/issues")
    @Operation(summary = "List all issues")
    public ResponseEntity<NewAPIResponse<IssueListResponseData>> listIssues(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        var issueList = issueQueryService.getAllIssues(page, size);
        
        return ResponseEntity.ok(
            NewAPIResponse.<IssueListResponseData>builder()
                .data(issueList)
                .build()
        );
    }

    @PatchMapping("/issue/{issueId}")
    @Operation(summary = "Update issue")
    public ResponseEntity<NewAPIResponse<IssueActionResponseData>> patchIssue(
            @PathVariable Long issueId,
            @RequestBody @Valid IssuePatchRequest request) {
        
        var issueData = issuePatchService.patch(issueId, request);
        
        return ResponseEntity.ok(
            NewAPIResponse.<IssueActionResponseData>builder()
                .data(issueData)
                .build()
        );
    }

    @PostMapping("/issue")
    @Operation(summary = "Create new issue")
    public ResponseEntity<NewAPIResponse<IssueActionResponseData>> createIssue(
            @RequestBody @Valid IssueCreateRequest request) {
        
        var issueData = issueActionService.createIssue(request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(
            NewAPIResponse.<IssueActionResponseData>builder()
                .data(issueData)
                .build()
        );
    }

    @PostMapping("/issue/{issueId}/report")
    @Operation(summary = "Report an issue")
    public ResponseEntity<NewAPIResponse<IssueActionResponseData>> reportIssue(
            @PathVariable Long issueId,
            @RequestBody @Valid IssueReportRequest request) {
        
        var issueData = issueActionService.reportIssue(issueId, request);
        
        return ResponseEntity.ok(
            NewAPIResponse.<IssueActionResponseData>builder()
                .data(issueData)
                .build()
        );
    }
}
```

### Test Updates

```java
@ExtendWith(MockitoExtension.class)
class IssueQueryServiceTest {

    @Mock
    private IssueRepository issueRepository;
    
    @Mock
    private IssueViewMapper issueViewMapper;

    private IssueQueryService issueQueryService;

    @BeforeEach
    void setUp() {
        issueQueryService = new IssueQueryService(issueRepository, issueViewMapper);
    }

    @Test
    void testGet_WithValidIssueId_ReturnsIssueResponseData() {
        // Arrange
        Long issueId = 1L;
        IssueEntity entity = IssueEntity.builder()
            .id(issueId)
            .title("Test Issue")
            .build();
        
        Issue issue = Issue.builder()
            .id(issueId)
            .title("Test Issue")
            .build();

        when(issueRepository.findById(issueId))
            .thenReturn(Optional.of(entity));
        when(issueViewMapper.map(entity))
            .thenReturn(issue);

        // Act
        IssueResponseData response = issueQueryService.get(issueId);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getIssue());
        assertEquals(issueId, response.getIssue().getId());
        assertEquals("Test Issue", response.getIssue().getTitle());
        
        verify(issueRepository).findById(issueId);
        verify(issueViewMapper).map(entity);
    }

    @Test
    void testGet_WithInvalidIssueId_ThrowsException() {
        // Arrange
        when(issueRepository.findById(999L))
            .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, 
            () -> issueQueryService.get(999L));
    }
}
```

---

## 4. MediaController Refactoring

### Current State

```java
@RestController
@RequestMapping("/media")
public class MediaController {
    
    @PostMapping("/upload")
    public ResponseEntity<APIResponse> uploadMedia(...) {
        var signedUrl = mediaService.generateSignedUrl(...);
        return ResponseEntity.ok(
            APIResponse.builder()
                .data(ResponseData.builder().mediaUrl(signedUrl).build())
                .build()
        );
    }
}
```

### Issues to Fix
- ❌ Returns old `APIResponse` format
- ❌ `MediaUploadResponseData` exists but not being used
- ❌ Service returns raw `SignedUrlResponse`, not wrapped

### Solution: Use Existing Response Object

**Response Object (already exists):**

```java
@Data
@Builder
public class MediaUploadResponseData {
    private SignedUrlResponse mediaUrl;
}
```

### Service Updates

```java
@Service
public class MediaService {
    
    // Before:
    public SignedUrlResponse generateSignedUrl(String fileName) {
        // ... logic ...
        return SignedUrlResponse.builder()...build();
    }

    // After:
    public MediaUploadResponseData generateSignedUrl(String fileName) {
        SignedUrlResponse signedUrl = // ... existing logic ...
        
        return MediaUploadResponseData.builder()
            .mediaUrl(signedUrl)
            .build();
    }
}
```

### Controller Updates

```java
@RestController
@RequestMapping("/media")
@Tag(name = "Media", description = "Media upload and management APIs")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/upload")
    @Operation(summary = "Generate signed URL for media upload")
    public ResponseEntity<NewAPIResponse<MediaUploadResponseData>> uploadMedia(
            @RequestParam String fileName,
            @RequestParam String contentType) {

        var mediaData = mediaService.generateSignedUrl(fileName, contentType);
        
        return ResponseEntity.ok(
            NewAPIResponse.<MediaUploadResponseData>builder()
                .data(mediaData)
                .build()
        );
    }
}
```

### Test Updates

```java
@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private StorageService storageService;

    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaService(storageService);
    }

    @Test
    void testGenerateSignedUrl_ReturnsMediaUploadResponseData() {
        // Arrange
        String fileName = "test-image.jpg";
        SignedUrlResponse signedUrl = SignedUrlResponse.builder()
            .url("https://storage.example.com/signed-url")
            .expiry(3600L)
            .build();

        when(storageService.generateSignedUrl(fileName))
            .thenReturn(signedUrl);

        // Act
        MediaUploadResponseData response = mediaService.generateSignedUrl(fileName);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getMediaUrl());
        assertEquals("https://storage.example.com/signed-url", response.getMediaUrl().getUrl());
        
        verify(storageService).generateSignedUrl(fileName);
    }
}
```

---

## 5. IssueHomeV2Controller Refactoring

### Current State

```java
@RestController
@RequestMapping("/api/v2")
public class IssueHomeV2Controller {
    
    @GetMapping("/issues/home")
    public ResponseEntity<APIResponse> getHome(...) {
        var issues = issueHomeService.getHomeIssues(...);
        return ResponseEntity.ok(
            APIResponse.builder()
                .data(ResponseData.builder().issues(issues).build())
                .build()
        );
    }
}
```

### Issues to Fix
- ❌ Returns old `APIResponse` format
- ❌ `IssueListResponseData` exists but not being used
- ❌ Doesn't provide structured response

### Solution: Use Existing Response Object

**Use IssueListResponseData:**

```java
@Data
@Builder
public class IssueListResponseData {
    private List<Issue> issues;
}
```

### Service Updates

```java
@Service
public class IssueHomeService {
    
    // Before:
    public List<Issue> getHomeIssues(String accessToken, Double lat, Double lng, int page, int size) {
        // ... logic ...
        return issues;
    }

    // After:
    public IssueListResponseData getHomeIssues(String accessToken, Double lat, Double lng, int page, int size) {
        List<Issue> issues = // ... existing logic to fetch and map issues ...
        
        return IssueListResponseData.builder()
            .issues(issues)
            .build();
    }
}
```

### Controller Updates

```java
@RestController
@RequestMapping("/api/v2")
@Tag(name = "Issue Home", description = "Home feed with nearby issues")
public class IssueHomeV2Controller {

    private final IssueHomeService issueHomeService;

    public IssueHomeV2Controller(IssueHomeService issueHomeService) {
        this.issueHomeService = issueHomeService;
    }

    @GetMapping("/issues/home")
    @Operation(summary = "Get home feed with nearby issues")
    public ResponseEntity<NewAPIResponse<IssueListResponseData>> getHome(
            @RequestHeader(value = "Authorization") String authorization,
            @RequestParam(value = "lat", required = false) Double latitude,
            @RequestParam(value = "lng", required = false) Double longitude,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String accessToken = extractBearerToken(authorization);
        
        if (accessToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(NewAPIResponse.<IssueListResponseData>builder().data(null).build());
        }

        var issueList = issueHomeService.getHomeIssues(accessToken, latitude, longitude, page, size);
        
        return ResponseEntity.ok(
            NewAPIResponse.<IssueListResponseData>builder()
                .data(issueList)
                .build()
        );
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
```

### Test Updates

```java
@ExtendWith(MockitoExtension.class)
class IssueHomeServiceTest {

    @Mock
    private IssueRepository issueRepository;
    
    @Mock
    private IssueViewMapper issueViewMapper;

    private IssueHomeService issueHomeService;

    @BeforeEach
    void setUp() {
        issueHomeService = new IssueHomeService(issueRepository, issueViewMapper);
    }

    @Test
    void testGetHomeIssues_WithValidToken_ReturnsIssueListResponseData() {
        // Arrange
        String accessToken = "valid-token";
        Double latitude = 40.7128;
        Double longitude = -74.0060;

        List<IssueEntity> entities = List.of(
            IssueEntity.builder().id(1L).title("Issue 1").build(),
            IssueEntity.builder().id(2L).title("Issue 2").build()
        );

        when(issueRepository.findNearby(latitude, longitude, 10))
            .thenReturn(entities);
        when(issueViewMapper.map(any(IssueEntity.class)))
            .thenAnswer(inv -> Issue.builder()
                .id(((IssueEntity) inv.getArgument(0)).getId())
                .title(((IssueEntity) inv.getArgument(0)).getTitle())
                .build());

        // Act
        IssueListResponseData response = issueHomeService.getHomeIssues(accessToken, latitude, longitude, 0, 10);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getIssues());
        assertEquals(2, response.getIssues().size());
        assertEquals("Issue 1", response.getIssues().get(0).getTitle());
        
        verify(issueRepository).findNearby(latitude, longitude, 10);
    }
}
```

---

## Implementation Checklist

Use this checklist for each of the 5 HIGH PRIORITY controllers:

### ☑️ AuthController
- [ ] Create `GoogleAuthResponseData` class
- [ ] Update `GoogleAuthService.handleAuthorizationCode()` to return `GoogleAuthResponseData`
- [ ] Update `GoogleAuthService.handleAccessToken()` to return `GoogleAuthResponseData`
- [ ] Update `AuthController.googleCallback()` method
- [ ] Update `AuthController.authenticateWithAccessToken()` method
- [ ] Create/Update unit tests for service
- [ ] Create/Update integration tests for controller
- [ ] Test all endpoints manually

### ☑️ ProfileController
- [ ] Update `GetProfileService.getMyProfile()` to return `UserProfileResponseData`
- [ ] Update `ProfileController.getMyProfile()` method
- [ ] Update unit tests for service
- [ ] Update integration tests for controller
- [ ] Test endpoint manually

### ☑️ IssueController
- [ ] Update `IssueQueryService.get()` to return `IssueResponseData`
- [ ] Update `IssueQueryService.getAllIssues()` to return `IssueListResponseData`
- [ ] Update `IssuePatchService.patch()` to return `IssueActionResponseData`
- [ ] Update `IssueActionService` methods to return response objects
- [ ] Update all controller methods
- [ ] Update all service tests
- [ ] Update all controller tests
- [ ] Test all endpoints manually

### ☑️ MediaController
- [ ] Update `MediaService.generateSignedUrl()` to return `MediaUploadResponseData`
- [ ] Update `MediaController.uploadMedia()` method
- [ ] Update service tests
- [ ] Update controller tests
- [ ] Test endpoint manually

### ☑️ IssueHomeV2Controller
- [ ] Update `IssueHomeService.getHomeIssues()` to return `IssueListResponseData`
- [ ] Update `IssueHomeV2Controller.getHome()` method
- [ ] Update service tests
- [ ] Update controller tests
- [ ] Test endpoint manually

---

## Summary

| Controller | Response Objects | Status | Impact |
|-----------|-----------------|--------|--------|
| AuthController | `GoogleAuthResponseData`, `AuthTokenResponseData` ✅ | 2 endpoints need refactoring | Authentication - Critical |
| ProfileController | `UserProfileResponseData` ✅ | 1+ endpoints need refactoring | User management - High |
| IssueController | `IssueResponseData`, `IssueListResponseData`, `IssueActionResponseData` ✅ | Multiple endpoints | Core feature - Critical |
| MediaController | `MediaUploadResponseData` ✅ | 1+ endpoints | Content management - High |
| IssueHomeV2Controller | `IssueListResponseData` ✅ | 1+ endpoints | Feed/discovery - High |

**Total Effort:** ~20-30 endpoints across 5 controllers
**Expected Benefits:** 
- ✅ Type-safe API responses
- ✅ Cleaner, more maintainable code
- ✅ Better test coverage
- ✅ Clearer API contracts
- ✅ Improved developer experience


Cuurent step

implement the change but dont changes the tests, test will be next step