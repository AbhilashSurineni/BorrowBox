package com.borrowbox.common.settings;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "settings")
public class Setting {
  @Id
  @Column(name = "setting_key", length = 60)
  public String key;

  /** JSON text (numbers for every current setting). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "json")
  public String value;

  @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
  public Instant updatedAt;

  @PrePersist
  @PreUpdate
  void touch() {
    updatedAt = Instant.now();
  }
}
