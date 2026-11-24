package org.smalltech.hashtaglocal_backend.model.request;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * AI provider settings from the Ops Dashboard, for save and for "Test connection".
 *
 * <p>Save: {@code provider} and {@code model} are required; a blank {@code endpoint} means the
 * provider's default; a blank {@code apiKey} keeps the saved key. Test: any blank field falls back
 * to the saved config.
 */
@Data
public class AiProviderConfigRequest {

  @Size(max = 50)
  private String provider;

  @Size(max = 500)
  private String endpoint;

  @Size(max = 200)
  private String model;

  /** Write-only. Excluded from toString so it can never reach a log line. */
  @ToString.Exclude
  @Size(max = 500)
  private String apiKey;
}
