package com.borrowbox.reviews;

import com.borrowbox.common.web.Ids;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reviews")
public class Review {
  @Id
  @Column(columnDefinition = "char(36)")
  public String id;

  @Column(name = "loan_id", nullable = false, columnDefinition = "char(36)")
  public String loanId;

  @Column(name = "reviewer_id", nullable = false, columnDefinition = "char(36)")
  public String reviewerId;

  @Column(name = "reviewee_id", nullable = false, columnDefinition = "char(36)")
  public String revieweeId;

  @Column(nullable = false, columnDefinition = "smallint")
  public int rating;

  @Column(columnDefinition = "text")
  public String comment;

  @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
  public Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = Ids.newId();
    if (createdAt == null) createdAt = Instant.now();
  }
}
