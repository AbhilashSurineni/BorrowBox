package com.borrowbox.items;

import com.borrowbox.common.web.Ids;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "item_images")
public class ItemImage {
  @Id
  @Column(columnDefinition = "char(36)")
  public String id;

  @Column(name = "item_id", nullable = false, columnDefinition = "char(36)")
  public String itemId;

  @Column(nullable = false, columnDefinition = "text")
  public String url;

  @Column(nullable = false)
  public int position;

  @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
  public Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = Ids.newId();
    if (createdAt == null) createdAt = Instant.now();
  }
}
