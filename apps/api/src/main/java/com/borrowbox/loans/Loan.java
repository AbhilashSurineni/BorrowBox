package com.borrowbox.loans;

import com.borrowbox.common.web.Ids;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Persistence struct for the loans table. Only {@link LoanService} may change {@link #status};
 * {@link #version} gives every status write an optimistic guard (the TS "WHERE status = from").
 */
@Entity
@Table(name = "loans")
public class Loan {
  @Id
  @Column(columnDefinition = "char(36)")
  public String id;

  @Column(name = "item_id", nullable = false, columnDefinition = "char(36)")
  public String itemId;

  @Column(name = "borrower_id", nullable = false, columnDefinition = "char(36)")
  public String borrowerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  public LoanStatus status = LoanStatus.PENDING;

  @Column(name = "start_date", nullable = false)
  public LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  public LocalDate endDate;

  @Column(name = "original_end_date", nullable = false)
  public LocalDate originalEndDate;

  @Column(columnDefinition = "text")
  public String message;

  @Column(name = "rejection_reason", columnDefinition = "text")
  public String rejectionReason;

  @Column(name = "cancellation_reason", columnDefinition = "text")
  public String cancellationReason;

  @Column(name = "cancelled_by_id", columnDefinition = "char(36)")
  public String cancelledById;

  @Column(name = "approved_at", columnDefinition = "datetime(6)")
  public Instant approvedAt;

  @Column(name = "handover_marked_at", columnDefinition = "datetime(6)")
  public Instant handoverMarkedAt;

  @Column(name = "handed_over_at", columnDefinition = "datetime(6)")
  public Instant handedOverAt;

  @Column(name = "return_marked_by_id", columnDefinition = "char(36)")
  public String returnMarkedById;

  @Column(name = "return_marked_at", columnDefinition = "datetime(6)")
  public Instant returnMarkedAt;

  @Column(name = "returned_at", columnDefinition = "datetime(6)")
  public Instant returnedAt;

  @Column(name = "return_condition_note", columnDefinition = "text")
  public String returnConditionNote;

  @Enumerated(EnumType.STRING)
  @Column(name = "extension_status", nullable = false, length = 10)
  public ExtensionStatus extensionStatus = ExtensionStatus.NONE;

  @Column(name = "extension_requested_until")
  public LocalDate extensionRequestedUntil;

  @Column(name = "extension_message", columnDefinition = "text")
  public String extensionMessage;

  @Column(name = "completed_at", columnDefinition = "datetime(6)")
  public Instant completedAt;

  @Version
  @Column(nullable = false)
  public long version;

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
