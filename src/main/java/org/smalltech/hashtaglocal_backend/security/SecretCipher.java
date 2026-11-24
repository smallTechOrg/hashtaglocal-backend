package org.smalltech.hashtaglocal_backend.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.smalltech.hashtaglocal_backend.service.ai.AiConfigProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption for secrets stored in the database (AI provider API keys).
 *
 * <p>The master key comes only from {@code AI_CONFIG_ENCRYPTION_KEY} (base64, 32 bytes), never from
 * the database or source. Output is {@code v1:<base64(nonce || ciphertext+tag)>}; the version
 * prefix leaves room for key rotation. A fresh 12-byte random nonce is used per encryption, and
 * {@code context} is bound as associated data so a ciphertext cannot be moved to another column.
 */
@Component
public class SecretCipher {

  private static final String VERSION = "v1";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int KEY_BYTES = 32;
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKey key;
  private final SecureRandom random = new SecureRandom();

  @Autowired
  public SecretCipher(AiConfigProperties properties) {
    this(properties.getEncryptionKey());
  }

  /**
   * @param base64Key base64 of 32 random bytes; null or blank leaves the cipher unconfigured
   * @throws IllegalStateException when the key is set but malformed, so a bad secret fails startup
   *     instead of failing later
   */
  public SecretCipher(String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      this.key = null;
      return;
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(base64Key.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("AI_CONFIG_ENCRYPTION_KEY is not valid base64");
    }
    if (bytes.length != KEY_BYTES) {
      throw new IllegalStateException(
          "AI_CONFIG_ENCRYPTION_KEY must decode to " + KEY_BYTES + " bytes, got " + bytes.length);
    }
    this.key = new SecretKeySpec(bytes, "AES");
  }

  /** True when a master key is set. */
  public boolean isConfigured() {
    return key != null;
  }

  public String encrypt(String plaintext, String context) {
    requireKey();
    try {
      byte[] nonce = new byte[NONCE_BYTES];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
      byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] out = ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array();
      return VERSION + ":" + Base64.getEncoder().encodeToString(out);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Encryption failed", e);
    }
  }

  /**
   * @throws SecretDecryptionException when the value was tampered with, was encrypted under another
   *     master key, or is malformed
   */
  public String decrypt(String stored, String context) {
    requireKey();
    if (stored == null || !stored.startsWith(VERSION + ":")) {
      throw new SecretDecryptionException("Unknown ciphertext format");
    }
    try {
      byte[] in = Base64.getDecoder().decode(stored.substring(VERSION.length() + 1));
      if (in.length <= NONCE_BYTES) {
        throw new SecretDecryptionException("Ciphertext too short");
      }
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, NONCE_BYTES));
      cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
      byte[] plain = cipher.doFinal(in, NONCE_BYTES, in.length - NONCE_BYTES);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException | GeneralSecurityException e) {
      // Never include the ciphertext or key material in the message.
      throw new SecretDecryptionException("Stored secret could not be decrypted");
    }
  }

  private void requireKey() {
    if (key == null) {
      throw new IllegalStateException("AI_CONFIG_ENCRYPTION_KEY is not set");
    }
  }

  /** A stored secret could not be decrypted. Carries no secret material. */
  public static class SecretDecryptionException extends RuntimeException {
    public SecretDecryptionException(String message) {
      super(message);
    }
  }
}
