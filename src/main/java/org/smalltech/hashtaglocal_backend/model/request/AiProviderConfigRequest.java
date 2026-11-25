package org.smalltech.hashtaglocal_backend.model.request;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/**
 * An AI connection from the Ops Dashboard, for save and for "Test": a name the admin chooses, an
 * OpenAI-compatible endpoint, a model and an API key.
 *
 * <p>Save: {@code name}, {@code endpoint} and {@code model} are required; a blank {@code apiKey}
 * keeps the saved key (same host only). Test: any blank field falls back to the saved connection.
 */
@Data
public class AiProviderConfigRequest {

  @Size(max = 100)
  private String name;

  @Size(max = 500)
  private String endpoint;

  @Size(max = 200)
  private String model;

  /** Write-only. Excluded from toString so it can never reach a log line. */
  @ToString.Exclude
  @Size(max = 500)
  private String apiKey;
}
