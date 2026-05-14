package org.smalltech.hashtaglocal_backend.service;

import org.smalltech.hashtaglocal_backend.model.response.AuthTokenResponseData;

/**
 * Common contract for all OAuth provider auth services (Google, Apple, etc.).
 * Implement this interface when adding a new auth provider.
 */
public interface OAuthService {

  String getProviderType();
}
