package com.borrowbox.auth;

import com.borrowbox.common.web.Ids;
import jakarta.persistence.*;
import java.time.Instant;

/** Single-use email-verification / password-reset token, stored as its SHA-256 hash. */
@Entity
@Table(name = "auth_tokens")
public class AuthToken {
  @Id
  @Column(columnDefinition = "char(36)")
  public String id;

  @Column(name = "user_id", nullable = false, columnDefinition = "char(36)")
  public String userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public AuthTokenType type;

  @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "char(64)")
  public String tokenHash;

  @Column(name = "expires_at", nullable = false, columnDefinition = "datetime(6)")
  public Instant expiresAt;

  @Column(name = "used_at", columnDefinition = "datetime(6)")
  public Instant usedAt;

  @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
  public Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = Ids.newId();
    if (createdAt == null) createdAt = Instant.now();
  }
}
