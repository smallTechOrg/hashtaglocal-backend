package org.smalltech.hashtaglocal_backend.service.ai;

/** Why an AI call failed. {@link AiFallbackChain} picks the next slot and cooldown from this. */
public enum AiFailure {
  /** Too many requests right now (per-minute limit). Comes back in seconds. */
  RATE_LIMITED,
  /** Daily quota or account credit used up. Comes back in hours. */
  QUOTA_EXHAUSTED,
  /** Key is wrong, revoked or blocked. Does not come back until someone fixes the key. */
  AUTH_FAILED,
  /** Model does not exist or is retired for this key. */
  MODEL_UNAVAILABLE,
  /** Server error, overload or timeout. Usually a short blip. */
  TRANSIENT,
  /** The call worked but the reply had no usable text. */
  INVALID_RESPONSE,
  /**
   * The provider rejected our request itself. Retrying with another key would fail the same way.
   */
  BAD_REQUEST
}
