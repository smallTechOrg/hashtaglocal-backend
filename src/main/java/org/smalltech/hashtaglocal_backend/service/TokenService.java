package org.smalltech.hashtaglocal_backend.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Url = Base64.getUrlEncoder().withoutPadding();

    @Value("${auth.access-token.expiry-seconds:3600}")
    private long accessTokenExpirySeconds;

    @Value("${auth.refresh-token.expiry-seconds:604800}")
    private long refreshTokenExpirySeconds;

    public String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return base64Url.encodeToString(bytes);
    }

    public long nowEpochSeconds() {
        return Instant.now().getEpochSecond();
    }

    public long accessExpiryEpochSeconds() {
        return nowEpochSeconds() + accessTokenExpirySeconds;
    }

    public long refreshExpiryEpochSeconds() {
        return nowEpochSeconds() + refreshTokenExpirySeconds;
    }
}
