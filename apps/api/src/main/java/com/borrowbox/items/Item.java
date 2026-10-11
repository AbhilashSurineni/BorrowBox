package com.borrowbox.items;

import com.borrowbox.common.web.Ids;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "items")
public class Item {
  @Id
  @Column(columnDefinition = "char(36)")
  public String id;

  @Column(name = "owner_id", nullable = false, columnDefinition = "char(36)")
  public String ownerId;

  @Column(name = "category_id", nullable = false, columnDefinition = "char(36)")
  public String categoryId;

  @Column(nullable = false, length = 150)
  public String name;

  @Column(nullable = false, columnDefinition = "text")
  public String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "item_condition", nullable = false, length = 4)
  public ItemCondition condition;

  @Column(name = "max_loan_days", nullable = false)
  public int maxLoanDays = 14;

  @Column(name = "pickup_location", nullable = false, length = 200)
  public String pickupLocation;

  @Column(name = "deposit_note", length = 500)
  public String depositNote;

  @Column(name = "is_paused", nullable = false)
  public boolean isPaused;

  @Column(name = "is_removed", nullable = false)
  public boolean isRemoved;

  @Column(name = "removed_reason", columnDefinition = "text")
  public String removedReason;

  @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
  public BigDecimal ratingAvg = BigDecimal.ZERO;

  @Column(name = "rating_count", nullable = false)
  public int ratingCount;

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
}
