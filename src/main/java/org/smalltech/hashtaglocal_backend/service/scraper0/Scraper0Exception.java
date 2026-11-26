package org.smalltech.hashtaglocal_backend.service.scraper0;

import lombok.Getter;

/**
 * A failed scraper0 call: the v2 error {@code code} (e.g. {@code site_not_onboarded}, {@code
 * outcome_unknown}) and the HTTP status scraper0 answered with.
 *
 * <p>Failures that never got an answer use status 0 and a local code: {@code not_configured},
 * {@code timeout}, {@code unreachable}, {@code invalid_response}. A lookup that is still running
 * (202) uses status 202 and code {@code running}.
 */
@Getter
public class Scraper0Exception extends RuntimeException {

  public static final String NOT_CONFIGURED = "not_configured";
  public static final String TIMEOUT = "timeout";
  public static final String UNREACHABLE = "unreachable";
  public static final String INVALID_RESPONSE = "invalid_response";
  public static final String RUNNING = "running";
  public static final String SITE_NOT_ONBOARDED = "site_not_onboarded";
  public static final String ACTION_NOT_AVAILABLE = "action_not_available";
  public static final String OUTCOME_UNKNOWN = "outcome_unknown";
  public static final String ACTION_FAILED = "action_failed";

  private final int httpStatus;
  private final String code;

  /** The scraper0 job, when the error names one (202 running, 409 outcome_unknown). */
  private final String jobId;

  public Scraper0Exception(int httpStatus, String code, String message, String jobId) {
    super(message);
    this.httpStatus = httpStatus;
    this.code = code;
    this.jobId = jobId;
  }

  public Scraper0Exception(int httpStatus, String code, String message) {
    this(httpStatus, code, message, null);
  }

  /** True when the site or action has not been set up in scraper0 yet. */
  public boolean isNotOnboarded() {
    return SITE_NOT_ONBOARDED.equals(code) || ACTION_NOT_AVAILABLE.equals(code);
  }

  /** {@code "<code>: <message>"}, as shown to admins. */
  public String describe() {
    return code + ": " + getMessage();
  }
}
