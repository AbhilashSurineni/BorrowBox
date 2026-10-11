package com.borrowbox.admin;

import com.borrowbox.common.web.Ids;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "announcements")
public class Announcement {
  @Id
  @Column(columnDefinition = "char(36)")
  public String id;

  @Column(name = "author_id", nullable = false, columnDefinition = "char(36)")
  public String authorId;

  @Column(nullable = false, length = 150)
  public String title;

  @Column(nullable = false, columnDefinition = "text")
  public String body;

  @Column(columnDefinition = "text")
  public String link;

  @Column(nullable = false, length = 20)
  public String audience;

  @Column(name = "send_email", nullable = false)
  public boolean sendEmail;

  @Column(name = "recipient_count", nullable = false)
  public int recipientCount;

  @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
  public Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = Ids.newId();
    if (createdAt == null) createdAt = Instant.now();
  }
}
