package com.borrowbox.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class TokenHashing {
  private static final SecureRandom RANDOM = new SecureRandom();

  private TokenHashing() {}

  public static String sha256Hex(String raw) {
    return HexFormat.of().formatHex(sha256(raw.getBytes(StandardCharsets.UTF_8)));
  }

  public static byte[] sha256(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** 32 random bytes, base64url without padding. */
  public static String randomToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
