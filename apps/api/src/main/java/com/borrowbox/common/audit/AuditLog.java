package com.borrowbox.common.audit;

import com.borrowbox.common.web.Ids;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
  @Id
  @Column(columnDefinition = "char(36)")
  public String id;

  @Column(name = "actor_id", columnDefinition = "char(36)")
  public String actorId;

  @Column(nullable = false, length = 60)
  public String action;

  @Column(nullable = false, length = 40)
  public String entity;

  @Column(name = "entity_id", length = 64)
  public String entityId;

  /** JSON text. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  public String metadata;

  @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
  public Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = Ids.newId();
    if (createdAt == null) createdAt = Instant.now();
  }
}
