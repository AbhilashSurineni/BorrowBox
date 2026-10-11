package com.borrowbox.items;

import com.borrowbox.common.web.Ids;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "categories")
public class Category {
  @Id
  @Column(columnDefinition = "char(36)")
  public String id;

  @Column(nullable = false, length = 60, unique = true)
  public String name;

  @Column(length = 60)
  public String icon;

  @Column(name = "is_active", nullable = false)
  public boolean isActive = true;

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
