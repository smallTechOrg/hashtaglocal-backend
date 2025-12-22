package org.smalltech.hashtaglocal_backend.controller;

import org.smalltech.hashtaglocal_backend.model.IssueResponse;
import org.smalltech.hashtaglocal_backend.model.IssueResponse.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/issue")
@Tag(name = "Issue", description = "issue API")

public class IssueController {

    @GetMapping("/mock")
    @Operation(
        summary = "Get mock issue",
        description = "Returns a mock issue response with user, location, locality and viewer context."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Successful mock issue response",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = IssueResponse.class)
            )
    )
    public IssueResponse getMockIssue() {

        // ---- Media Location ----
        MediaLocation mediaLocation =
                new MediaLocation("12.34", "56.78");

        // ---- Media ----
        Media media = new Media(
                mediaLocation,
                "photo",
                "https://example.com/image.jpg"
        );

        // ---- User ----
        User user = new User(
                "john_doe",
                "https://example.com/profile.jpg"
        );

        // ---- Locality ----
        Locality locality = new Locality(
                List.of("#Jaipur")
        );

        // ---- Location ----
        Location location = new Location(
                "12.34",
                "56.78",
                locality,
                "Main Street",
                "Near City Mall"
        );

        // ---- Issue ----
        Issue issue = new Issue(
                user,
                location,
                "road",
                "Large pothole causing traffic issues",
                LocalDateTime.now(),
                List.of(media),
                42,
                10,
                "OPEN",
                1
        );

        // ---- Viewer Context ----
        ViewerContext viewerContext = new ViewerContext(true);

        // ---- Data ----
        Data data = new Data(issue, viewerContext);

        return new IssueResponse(data);
    }
}
