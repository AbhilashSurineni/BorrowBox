package com.borrowbox.common.security;

import com.borrowbox.common.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** HS256 access tokens (15 min) and refresh-token JWTs. Keys are SHA-256 of the configured secrets. */
@Service
public class JwtService {
  private final SecretKey accessKey;
  private final SecretKey refreshKey;
  private final AppProperties props;

  public record AccessClaims(String userId, String role) {}

  public record RefreshClaims(String userId, String jti, String familyId) {}

  public JwtService(AppProperties props) {
    this.props = props;
    this.accessKey = key(props.jwt().secret(), "JWT_SECRET");
    this.refreshKey = key(props.jwt().refreshSecret(), "JWT_REFRESH_SECRET");
  }

  private static SecretKey key(String secret, String name) {
    if (secret == null || secret.length() < 16) {
      throw new IllegalStateException(name + " must be set (at least 16 characters)");
    }
    return Keys.hmacShaKeyFor(TokenHashing.sha256(secret.getBytes(StandardCharsets.UTF_8)));
  }

  public String signAccessToken(String userId, String role) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId)
        .claim("role", role)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(props.jwt().accessTokenTtl())))
        .signWith(accessKey)
        .compact();
  }

  public Optional<AccessClaims> verifyAccessToken(String token) {
    try {
      Claims c = Jwts.parser().verifyWith(accessKey).build().parseSignedClaims(token).getPayload();
      return c.getSubject() == null ? Optional.empty() : Optional.of(new AccessClaims(c.getSubject(), String.valueOf(c.get("role"))));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  public String signRefreshToken(String userId, String jti, String familyId, Instant expiresAt) {
    return Jwts.builder()
        .subject(userId)
        .id(jti)
        .claim("fam", familyId)
        .issuedAt(new Date())
        .expiration(Date.from(expiresAt))
        .signWith(refreshKey)
        .compact();
  }

  public Optional<RefreshClaims> verifyRefreshToken(String token) {
    try {
      Claims c = Jwts.parser().verifyWith(refreshKey).build().parseSignedClaims(token).getPayload();
      Object fam = c.get("fam");
      if (c.getSubject() == null || c.getId() == null || fam == null) return Optional.empty();
      return Optional.of(new RefreshClaims(c.getSubject(), c.getId(), fam.toString()));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
