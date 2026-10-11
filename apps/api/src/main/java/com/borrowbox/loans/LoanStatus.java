package com.borrowbox.loans;

import java.util.List;

public enum LoanStatus {
  PENDING, APPROVED, REJECTED, CANCELLED, EXPIRED, ACTIVE, OVERDUE, RETURNED, COMPLETED;

  /** Statuses that block the item's calendar (the old exclusion constraint's WHERE clause). */
  public static final List<LoanStatus> BLOCKING = List.of(APPROVED, ACTIVE, OVERDUE);

  /** Statuses after which the counterparty's contact details are revealed. */
  public static final List<LoanStatus> CONTACT_VISIBLE = List.of(APPROVED, ACTIVE, OVERDUE, RETURNED, COMPLETED);

  public static final List<LoanStatus> TERMINAL = List.of(REJECTED, CANCELLED, EXPIRED, COMPLETED);

  public boolean isBlocking() {
    return BLOCKING.contains(this);
  }
}
