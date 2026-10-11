package com.borrowbox.auth;

import jakarta.persistence.*;
import java.time.Instant;

/** A refresh token, stored as its SHA-256 hash. Tokens of one login share a family id. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
  @Id
  @Column(columnDefinition = "char(36)")
  public String id;

  @Column(name = "user_id", nullable = false, columnDefinition = "char(36)")
  public String userId;

  @Column(name = "family_id", nullable = false, columnDefinition = "char(36)")
  public String familyId;

  @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "char(64)")
  public String tokenHash;

  @Column(name = "expires_at", nullable = false, columnDefinition = "datetime(6)")
  public Instant expiresAt;

  @Column(name = "revoked_at", columnDefinition = "datetime(6)")
  public Instant revokedAt;

  @Column(name = "replaced_by", columnDefinition = "char(36)")
  public String replacedBy;

  @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
  public Instant createdAt;
}
