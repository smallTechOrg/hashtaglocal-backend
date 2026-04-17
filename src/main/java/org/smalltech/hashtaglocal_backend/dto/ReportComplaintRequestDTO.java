package org.smalltech.hashtaglocal_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class ReportComplaintRequestDTO {

  @NotBlank(message = "source is required")
  String source;

  @Valid
  @NotNull(message = "context is required")
  Context context;

  @AssertTrue(message = "source must be GOV_PORTAL_ISSUE or GOV_ISSUE_PORTAL")
  private boolean isSourceValid() {
    if (source == null) {
      return false;
    }
    return "GOV_PORTAL_ISSUE".equals(source) || "GOV_ISSUE_PORTAL".equals(source);
  }

  @Value
  @Builder
  @Jacksonized
  public static class Context {
    @NotBlank(message = "context.portal is required")
    String portal;

    @Valid
    @NotNull(message = "context.action is required")
    Action action;

    @Valid
    @NotNull(message = "context.auth is required")
    Auth auth;
  }

  @Value
  @Builder
  @Jacksonized
  public static class Action {
    @NotBlank(message = "context.action.type is required")
    String type;

    @Valid
    @NotNull(message = "context.action.data is required")
    Data data;

    @AssertTrue(message = "context.action.type must be REPORT_ISSUE")
    private boolean isActionTypeValid() {
      return "REPORT_ISSUE".equals(type);
    }
  }

  @Value
  @Builder
  @Jacksonized
  public static class Data {
    @NotBlank(message = "context.action.data.category is required")
    String category;

    @NotBlank(message = "context.action.data.sub_category is required")
    String subCategory;

    String description;
    String mediaUrl;
    String latitude;
    String longitude;
  }

  @Value
  @Builder
  @Jacksonized
  public static class Auth {
    @NotBlank(message = "context.auth.username is required")
    String username;

    @NotBlank(message = "context.auth.password is required")
    String password;
  }
}
