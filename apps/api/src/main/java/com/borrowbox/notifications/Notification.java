package com.borrowbox.notifications;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {
  @Id
  @Column(columnDefinition = "char(36)")
  public String id;

  @Column(name = "user_id", nullable = false, columnDefinition = "char(36)")
  public String userId;

  @Column(nullable = false, length = 40)
  public String type;

  @Column(nullable = false, length = 200)
  public String title;

  @Column(nullable = false, columnDefinition = "text")
  public String body;

  @Column(columnDefinition = "text")
  public String link;

  @Column(name = "is_read", nullable = false)
  public boolean isRead;

  @Column(name = "dedupe_key", length = 191, unique = true)
  public String dedupeKey;

  @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
  public Instant createdAt;
}
