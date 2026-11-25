package org.smalltech.hashtaglocal_backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

  private static final String CONTEXT = "ai_configs.api_key";
  private static final String SECRET = "sk-ant-api03-secret-value-1234";

  private final SecretCipher cipher = new SecretCipher(randomKey());

  @Test
  void roundTripsAndNeverContainsThePlaintext() {
    String stored = cipher.encrypt(SECRET, CONTEXT);

    assertTrue(stored.startsWith("v1:"));
    assertFalse(stored.contains(SECRET));
    assertFalse(stored.contains("1234"));
    assertEquals(SECRET, cipher.decrypt(stored, CONTEXT));
  }

  @Test
  void usesAFreshNonceEveryTime() {
    assertNotEquals(cipher.encrypt(SECRET, CONTEXT), cipher.encrypt(SECRET, CONTEXT));
  }

  @Test
  void rejectsTamperedCiphertext() {
    String stored = cipher.encrypt(SECRET, CONTEXT);
    byte[] raw = Base64.getDecoder().decode(stored.substring(3));
    raw[raw.length - 1] ^= 1;
    String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);

    assertThrows(
        SecretCipher.SecretDecryptionException.class, () -> cipher.decrypt(tampered, CONTEXT));
  }

  @Test
  void rejectsAnotherMasterKeyOrContext() {
    String stored = cipher.encrypt(SECRET, CONTEXT);

    assertThrows(
        SecretCipher.SecretDecryptionException.class,
        () -> new SecretCipher(randomKey()).decrypt(stored, CONTEXT));
    assertThrows(
        SecretCipher.SecretDecryptionException.class, () -> cipher.decrypt(stored, "other.column"));
  }

  @Test
  void decryptionErrorsCarryNoSecretMaterial() {
    String stored = cipher.encrypt(SECRET, CONTEXT);
    SecretCipher.SecretDecryptionException e =
        assertThrows(
            SecretCipher.SecretDecryptionException.class,
            () -> new SecretCipher(randomKey()).decrypt(stored, CONTEXT));
    assertFalse(e.getMessage().contains(stored.substring(3, 12)));
  }

  @Test
  void blankKeyLeavesItUnconfigured() {
    SecretCipher none = new SecretCipher("");
    assertFalse(none.isConfigured());
    assertThrows(IllegalStateException.class, () -> none.encrypt(SECRET, CONTEXT));
  }

  @Test
  void malformedKeyFailsFast() {
    assertThrows(IllegalStateException.class, () -> new SecretCipher("not base64 !!"));
    assertThrows(
        IllegalStateException.class,
        () -> new SecretCipher(Base64.getEncoder().encodeToString(new byte[16])));
  }

  static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }
}
