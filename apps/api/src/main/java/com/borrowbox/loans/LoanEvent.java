package com.borrowbox.loans;

import java.util.Locale;

/** Events that move a loan between states (Section 7). */
public enum LoanEvent {
  APPROVE, REJECT, CANCEL, EXPIRE, CONFIRM_HANDOVER, MARK_OVERDUE, CONFIRM_RETURN, COMPLETE, EXTEND_OVERDUE;

  /** "confirm handover", as used in the TS error messages. */
  public String phrase() {
    return name().toLowerCase(Locale.ROOT).replace('_', ' ');
  }
}
