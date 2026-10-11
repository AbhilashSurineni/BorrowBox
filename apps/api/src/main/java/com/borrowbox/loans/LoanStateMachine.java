package com.borrowbox.loans;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The Section 7 transition table: (from, event, to, allowed actors). The initial
 * "(start) -> PENDING" is the creation of a request. EXTEND_OVERDUE is the one addition: an
 * accepted extension whose new end date is today or later puts an OVERDUE loan back to ACTIVE.
 * SYSTEM covers scheduled jobs and cascades (auto-reject on approve, item removal, account deletion).
 */
public final class LoanStateMachine {
  public enum Actor { OWNER, BORROWER, SYSTEM }

  public record Transition(LoanStatus from, LoanEvent event, LoanStatus to, Set<Actor> actors) {}

  private static final Set<Actor> OWNER = EnumSet.of(Actor.OWNER);
  private static final Set<Actor> BORROWER = EnumSet.of(Actor.BORROWER);
  private static final Set<Actor> PARTIES = EnumSet.of(Actor.OWNER, Actor.BORROWER);
  private static final Set<Actor> SYSTEM = EnumSet.of(Actor.SYSTEM);

  public static final List<Transition> TRANSITIONS = List.of(
      new Transition(LoanStatus.PENDING, LoanEvent.APPROVE, LoanStatus.APPROVED, OWNER),
      new Transition(LoanStatus.PENDING, LoanEvent.REJECT, LoanStatus.REJECTED, EnumSet.of(Actor.OWNER, Actor.SYSTEM)),
      new Transition(LoanStatus.PENDING, LoanEvent.CANCEL, LoanStatus.CANCELLED, EnumSet.of(Actor.BORROWER, Actor.SYSTEM)),
      new Transition(LoanStatus.PENDING, LoanEvent.EXPIRE, LoanStatus.EXPIRED, SYSTEM),
      new Transition(LoanStatus.APPROVED, LoanEvent.CANCEL, LoanStatus.CANCELLED, EnumSet.of(Actor.OWNER, Actor.BORROWER, Actor.SYSTEM)),
      new Transition(LoanStatus.APPROVED, LoanEvent.CONFIRM_HANDOVER, LoanStatus.ACTIVE, BORROWER),
      new Transition(LoanStatus.ACTIVE, LoanEvent.CONFIRM_RETURN, LoanStatus.RETURNED, PARTIES),
      new Transition(LoanStatus.ACTIVE, LoanEvent.MARK_OVERDUE, LoanStatus.OVERDUE, SYSTEM),
      new Transition(LoanStatus.OVERDUE, LoanEvent.CONFIRM_RETURN, LoanStatus.RETURNED, PARTIES),
      new Transition(LoanStatus.OVERDUE, LoanEvent.EXTEND_OVERDUE, LoanStatus.ACTIVE, OWNER),
      new Transition(LoanStatus.RETURNED, LoanEvent.COMPLETE, LoanStatus.COMPLETED, EnumSet.of(Actor.SYSTEM, Actor.OWNER, Actor.BORROWER)));

  private LoanStateMachine() {}

  public static Optional<Transition> find(LoanStatus from, LoanEvent event) {
    return TRANSITIONS.stream().filter(t -> t.from() == from && t.event() == event).findFirst();
  }

  public static Optional<LoanStatus> next(LoanStatus from, LoanEvent event) {
    return find(from, event).map(Transition::to);
  }

  public static boolean canTransition(LoanStatus from, LoanEvent event, Actor actor) {
    return find(from, event).map(t -> t.actors().contains(actor)).orElse(false);
  }

  public static List<LoanStatus> sourceStatuses(LoanEvent event) {
    return TRANSITIONS.stream().filter(t -> t.event() == event).map(Transition::from).toList();
  }
}
