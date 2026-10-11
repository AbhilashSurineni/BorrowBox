package com.borrowbox.users;

import com.borrowbox.common.web.Ids;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Persistence struct for the users table (never serialised directly; see UserViews). */
@Entity
@Table(name = "users")
public class User {
  @Id
  @Column(columnDefinition = "char(36)")
  public String id;

  @Column(nullable = false, length = 100)
  public String name;

  @Column(nullable = false, length = 150, unique = true)
  public String email;

  @Column(name = "password_hash", nullable = false)
  public String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  public Role role = Role.MEMBER;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  public UserStatus status = UserStatus.ACTIVE;

  @Column(nullable = false, length = 100)
  public String department;

  @Column(length = 20)
  public String phone;

  @Column(name = "avatar_url", columnDefinition = "text")
  public String avatarUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "contact_preference", nullable = false, length = 10)
  public ContactPreference contactPreference = ContactPreference.EMAIL;

  @Column(name = "email_verified", nullable = false)
  public boolean emailVerified;

  @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
  public BigDecimal ratingAvg = BigDecimal.ZERO;

  @Column(name = "rating_count", nullable = false)
  public int ratingCount;

  @Column(name = "warning_count", nullable = false)
  public int warningCount;

  @Column(name = "deleted_at", columnDefinition = "datetime(6)")
  public Instant deletedAt;

  @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
  public Instant createdAt;

  @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
  public Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = Ids.newId();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }
}
