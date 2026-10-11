package com.borrowbox.loans;

import static org.assertj.core.api.Assertions.assertThat;

import com.borrowbox.loans.LoanStateMachine.Actor;
import com.borrowbox.reviews.ReviewService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests: the Section 7 transition table and the rating math. */
class LoanStateMachineTest {
  @Test
  void tableMatchesSection7() {
    assertThat(LoanStateMachine.TRANSITIONS).hasSize(11);
    assertThat(LoanStateMachine.next(LoanStatus.PENDING, LoanEvent.APPROVE)).contains(LoanStatus.APPROVED);
    assertThat(LoanStateMachine.next(LoanStatus.PENDING, LoanEvent.REJECT)).contains(LoanStatus.REJECTED);
    assertThat(LoanStateMachine.next(LoanStatus.PENDING, LoanEvent.CANCEL)).contains(LoanStatus.CANCELLED);
    assertThat(LoanStateMachine.next(LoanStatus.PENDING, LoanEvent.EXPIRE)).contains(LoanStatus.EXPIRED);
    assertThat(LoanStateMachine.next(LoanStatus.APPROVED, LoanEvent.CANCEL)).contains(LoanStatus.CANCELLED);
    assertThat(LoanStateMachine.next(LoanStatus.APPROVED, LoanEvent.CONFIRM_HANDOVER)).contains(LoanStatus.ACTIVE);
    assertThat(LoanStateMachine.next(LoanStatus.ACTIVE, LoanEvent.CONFIRM_RETURN)).contains(LoanStatus.RETURNED);
    assertThat(LoanStateMachine.next(LoanStatus.ACTIVE, LoanEvent.MARK_OVERDUE)).contains(LoanStatus.OVERDUE);
    assertThat(LoanStateMachine.next(LoanStatus.OVERDUE, LoanEvent.CONFIRM_RETURN)).contains(LoanStatus.RETURNED);
    assertThat(LoanStateMachine.next(LoanStatus.RETURNED, LoanEvent.COMPLETE)).contains(LoanStatus.COMPLETED);
  }

  @Test
  void illegalTransitionsAreRejected() {
    assertThat(LoanStateMachine.next(LoanStatus.ACTIVE, LoanEvent.CANCEL)).isEmpty();
    assertThat(LoanStateMachine.next(LoanStatus.COMPLETED, LoanEvent.APPROVE)).isEmpty();
    assertThat(LoanStateMachine.next(LoanStatus.REJECTED, LoanEvent.APPROVE)).isEmpty();
    assertThat(LoanStateMachine.next(LoanStatus.PENDING, LoanEvent.CONFIRM_HANDOVER)).isEmpty();
    for (LoanStatus terminal : LoanStatus.TERMINAL) {
      for (LoanEvent e : LoanEvent.values()) assertThat(LoanStateMachine.next(terminal, e)).isEmpty();
    }
  }

  @Test
  void actorsAreEnforced() {
    assertThat(LoanStateMachine.canTransition(LoanStatus.PENDING, LoanEvent.APPROVE, Actor.OWNER)).isTrue();
    assertThat(LoanStateMachine.canTransition(LoanStatus.PENDING, LoanEvent.APPROVE, Actor.BORROWER)).isFalse();
    assertThat(LoanStateMachine.canTransition(LoanStatus.PENDING, LoanEvent.CANCEL, Actor.OWNER)).isFalse();
    assertThat(LoanStateMachine.canTransition(LoanStatus.APPROVED, LoanEvent.CANCEL, Actor.OWNER)).isTrue();
    assertThat(LoanStateMachine.canTransition(LoanStatus.APPROVED, LoanEvent.CONFIRM_HANDOVER, Actor.OWNER)).isFalse();
    assertThat(LoanStateMachine.canTransition(LoanStatus.PENDING, LoanEvent.EXPIRE, Actor.SYSTEM)).isTrue();
    assertThat(LoanStateMachine.canTransition(LoanStatus.ACTIVE, LoanEvent.MARK_OVERDUE, Actor.BORROWER)).isFalse();
  }

  @Test
  void ratingAverageIsRoundedToTwoDecimals() {
    assertThat(ReviewService.ratingAverage((5 + 4 + 4) / 3.0)).isEqualByComparingTo(new BigDecimal("4.33"));
    assertThat(ReviewService.ratingAverage((5 + 4) / 2.0)).isEqualByComparingTo(new BigDecimal("4.50"));
    assertThat(ReviewService.ratingAverage((5 + 5 + 4) / 3.0)).isEqualByComparingTo(new BigDecimal("4.67"));
    assertThat(ReviewService.ratingAverage(0)).isEqualByComparingTo(BigDecimal.ZERO);
  }
}
