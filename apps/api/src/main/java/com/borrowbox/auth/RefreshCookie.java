package com.borrowbox.auth;

import com.borrowbox.common.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;

/** The rotating refresh token: httpOnly cookie "bb_refresh", scoped to /api/v1/auth. */
public final class RefreshCookie {
  public static final String NAME = "bb_refresh";
  public static final String PATH = "/api/v1/auth";

  private RefreshCookie() {}

  private static ResponseCookie.ResponseCookieBuilder base(AppProperties props, String value) {
    String sameSite = props.cookie().sameSite();
    boolean secure = props.cookie().secure() || "none".equalsIgnoreCase(sameSite);
    return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite(capitalize(sameSite)).path(PATH);
  }

  public static ResponseCookie issue(AppProperties props, String token, Instant expiresAt) {
    return base(props, token).maxAge(Duration.between(Instant.now(), expiresAt)).build();
  }

  public static ResponseCookie clear(AppProperties props) {
    return base(props, "").maxAge(0).build();
  }

  private static String capitalize(String s) {
    String v = s == null || s.isBlank() ? "lax" : s.toLowerCase();
    return Character.toUpperCase(v.charAt(0)) + v.substring(1);
  }
}
