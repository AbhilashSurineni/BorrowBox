package com.borrowbox.reports;

import com.borrowbox.common.web.Ids;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reports")
public class Report {
  @Id
  @Column(columnDefinition = "char(36)")
  public String id;

  @Column(name = "reporter_id", nullable = false, columnDefinition = "char(36)")
  public String reporterId;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false, length = 4)
  public ReportTargetType targetType;

  @Column(name = "target_id", nullable = false, columnDefinition = "char(36)")
  public String targetId;

  /** User accountable for the report: the reported user, or the owner of the reported item. */
  @Column(name = "target_user_id", nullable = false, columnDefinition = "char(36)")
  public String targetUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public ReportReason reason;

  @Column(nullable = false, columnDefinition = "text")
  public String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  public ReportStatus status = ReportStatus.OPEN;

  @Column(name = "admin_note", columnDefinition = "text")
  public String adminNote;

  @Column(name = "resolved_by", columnDefinition = "char(36)")
  public String resolvedById;

  @Column(name = "resolved_at", columnDefinition = "datetime(6)")
  public Instant resolvedAt;

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
}
