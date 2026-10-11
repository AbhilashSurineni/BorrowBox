package com.borrowbox.loans;

import static com.borrowbox.common.errors.BusinessException.conflict;
import static com.borrowbox.common.errors.BusinessException.forbidden;
import static com.borrowbox.common.errors.BusinessException.notFound;
import static com.borrowbox.common.errors.BusinessException.unprocessable;

import com.borrowbox.common.errors.BusinessException;
import com.borrowbox.common.errors.ErrorCode;
import com.borrowbox.common.security.AuthUser;
import com.borrowbox.common.settings.Settings;
import com.borrowbox.common.settings.SettingsService;
import com.borrowbox.common.time.CampusClock;
import com.borrowbox.common.web.PageQuery;
import com.borrowbox.common.web.Paged;
import com.borrowbox.items.Item;
import com.borrowbox.items.ItemRepository;
import com.borrowbox.loans.LoanStateMachine.Actor;
import com.borrowbox.loans.LoanViews.LoanContext;
import com.borrowbox.loans.LoanViews.LoanView;
import com.borrowbox.loans.LoanViews.ViewerRole;
import com.borrowbox.notifications.NotificationService;
import com.borrowbox.notifications.NotificationService.Notify;
import com.borrowbox.notifications.NotificationType;
import com.borrowbox.users.User;
import com.borrowbox.users.UserRepository;
import com.borrowbox.users.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LoanService is the only place that changes a loan's status. Every change goes through
 * {@link #transition} / {@link #bulkTransition}, which consult the Section 7 table
 * ({@link LoanStateMachine}) and are guarded against concurrent writers (optimistic @Version for
 * single loans, "WHERE status = from" for bulk updates).
 *
 * <p>Rule 3 (no overlapping bookings), MySQL edition: create/approve/extend lock the item row
 * (SELECT ... FOR UPDATE) and then check for overlapping APPROVED/ACTIVE/OVERDUE loans inside
 * the same transaction; BEFORE INSERT/UPDATE triggers reject anything that slips through. Both
 * surface as 409 DATES_UNAVAILABLE. See docs/DB_NOTES.md.
 */
@Service
public class LoanService {
  private static final List<LoanStatus> ACTIVE_FOR_LIMIT = LoanStatus.BLOCKING;

  private final LoanRepository loans;
  private final ItemRepository items;
  private final UserRepository users;
  private final LoanViews views;
  private final NotificationService notifications;
  private final SettingsService settings;
  private final CampusClock clock;

  public LoanService(
      LoanRepository loans,
      ItemRepository items,
      UserRepository users,
      LoanViews views,
      NotificationService notifications,
      SettingsService settings,
      CampusClock clock) {
    this.loans = loans;
    this.items = items;
    this.users = users;
    this.views = views;
    this.notifications = notifications;
    this.settings = settings;
    this.clock = clock;
  }

  /** Patch applied by a bulk transition (null = unchanged). */
  public record BulkPatch(String rejectionReason, String cancellationReason, String cancelledById, Instant completedAt) {
    public static final BulkPatch NONE = new BulkPatch(null, null, null, null);

    static BulkPatch reject(String reason) {
      return new BulkPatch(reason, null, null, null);
    }

    static BulkPatch cancel(String reason, String by) {
      return new BulkPatch(null, reason, by, null);
    }
  }

  // ---------------------------------------------------------------- state machine primitives

  private static BusinessException invalidTransition(LoanStatus status, LoanEvent event) {
    return conflict(ErrorCode.INVALID_TRANSITION, "Cannot " + event.phrase() + " a loan that is " + status);
  }

  /** Moves one loan along the table; the @Version column rejects concurrent changes on flush. */
  private LoanStatus transition(Loan loan, LoanEvent event, Actor actor) {
    var t = LoanStateMachine.find(loan.status, event).orElseThrow(() -> invalidTransition(loan.status, event));
    if (!t.actors().contains(actor)) throw forbidden("You are not allowed to do this");
    loan.status = t.to();
    loans.saveAndFlush(loan);
    return t.to();
  }

  /** Applies {@code event} to every listed loan in a valid source state. Returns the ids moved. */
  private List<String> bulkTransition(Collection<Loan> candidates, LoanEvent event, BulkPatch patch) {
    List<String> moved = new ArrayList<>();
    Instant now = Instant.now();
    for (LoanStatus from : LoanStateMachine.sourceStatuses(event)) {
      List<String> ids = candidates.stream().filter(l -> l.status == from).map(l -> l.id).toList();
      if (ids.isEmpty()) continue;
      LoanStatus to = LoanStateMachine.next(from, event).orElseThrow();
      loans.bulkTransition(ids, from, to, patch.rejectionReason(), patch.cancellationReason(), patch.cancelledById(), patch.completedAt(), now);
      moved.addAll(loans.findIdsWithStatus(ids, to));
    }
    return moved;
  }

  /** Serialises all loan writes for one item (create, approve, extend, deletion). */
  private Item lockItem(String itemId) {
    return items.lockById(itemId).orElseThrow(() -> notFound("Item"));
  }

  /** Serialises request creation/approval per borrower so the Rule 4 limits cannot be raced. */
  private void lockBorrower(String borrowerId) {
    users.lockById(borrowerId);
  }

  private boolean hasBlockingOverlap(String itemId, LocalDate start, LocalDate end, String excludeLoanId) {
    return loans.countBlockingOverlaps(itemId, start, end, excludeLoanId, clock.today()) > 0;
  }

  /** Finds the loan's item, locks it, then loads the loan (fresh, after the lock). */
  private LoanContext loadForUpdate(String loanId) {
    String itemId = loans.findItemIdById(loanId);
    if (itemId == null) throw notFound("Loan");
    lockItem(itemId);
    Loan loan = loans.findById(loanId).orElseThrow(() -> notFound("Loan"));
    return views.context(loan);
  }

  private static ViewerRole requireRole(LoanContext c, AuthUser user, ViewerRole... allowed) {
    ViewerRole role = LoanViews.roleOf(c, user);
    List<ViewerRole> allowedList = List.of(allowed);
    if (role == null || (role == ViewerRole.ADMIN && !allowedList.contains(ViewerRole.ADMIN))) {
      throw forbidden("Only the parties to this loan can do this");
    }
    if (!allowedList.contains(role)) {
      throw forbidden(role == ViewerRole.BORROWER ? "Only the item owner can do this" : "Only the borrower can do this");
    }
    return role;
  }

  private static Actor actorOf(ViewerRole role) {
    return role == ViewerRole.OWNER ? Actor.OWNER : Actor.BORROWER;
  }

  private static String link(String loanId) {
    return "/loans/" + loanId;
  }

  private LoanView reload(String loanId, AuthUser viewer) {
    return views.present(loans.findById(loanId).orElseThrow(), viewer);
  }

  private static long inclusiveDays(LocalDate start, LocalDate end) {
    return ChronoUnit.DAYS.between(start, end) + 1;
  }

  // ---------------------------------------------------------------- reads

  @Transactional(readOnly = true)
  public Paged<LoanView> list(AuthUser user, String role, List<LoanStatus> statuses, PageQuery q) {
    boolean all = statuses == null || statuses.isEmpty();
    Page<Loan> page = loans.findForUser(user.id(), role == null ? "any" : role, all, all ? List.of(LoanStatus.PENDING) : statuses,
        q.pageable(Sort.unsorted()));
    return Paged.of(views.present(page.getContent(), user), page.getTotalElements(), q);
  }

  @Transactional(readOnly = true)
  public LoanView get(AuthUser user, String loanId) {
    Loan loan = loans.findById(loanId).orElseThrow(() -> notFound("Loan"));
    LoanContext c = views.context(loan);
    if (LoanViews.roleOf(c, user) == null) throw notFound("Loan");
    return LoanViews.present(c, user);
  }

  // ---------------------------------------------------------------- borrower request

  public record CreateLoan(LocalDate startDate, LocalDate endDate, String message) {}

  /** (start) -> PENDING. Enforces business rules 1, 2, 3, 4 and 5. */
  @Transactional
  public LoanView create(AuthUser user, String itemId, CreateLoan input) {
    LocalDate today = clock.today();
    Item item = items.lockById(itemId).orElse(null);
    lockBorrower(user.id());
    if (item == null || item.deletedAt != null || item.isRemoved) throw notFound("Item");
    if (item.ownerId.equals(user.id())) throw forbidden(ErrorCode.OWN_ITEM, "You cannot borrow your own item");
    User owner = users.findById(item.ownerId).orElseThrow();
    if (item.isPaused || owner.status != UserStatus.ACTIVE || owner.isDeleted()) {
      throw conflict(ErrorCode.ITEM_UNAVAILABLE, "This item is not available for requests right now");
    }
    if (input.startDate().isBefore(today)) throw unprocessable(ErrorCode.INVALID_DATES, "Start date cannot be in the past");
    if (inclusiveDays(input.startDate(), input.endDate()) > item.maxLoanDays) {
      throw unprocessable(ErrorCode.EXCEEDS_MAX_DURATION, "This item can be borrowed for at most " + item.maxLoanDays + " days");
    }

    Settings s = settings.get();
    long overdue = loans.countByBorrowerIdAndStatus(user.id(), LoanStatus.OVERDUE);
    long pending = loans.countByBorrowerIdAndStatus(user.id(), LoanStatus.PENDING);
    long active = loans.countByBorrowerIdAndStatusIn(user.id(), ACTIVE_FOR_LIMIT);
    if (overdue > 0) throw forbidden(ErrorCode.OVERDUE_LOAN_EXISTS, "Return your overdue item before making new requests");
    if (pending >= s.maxPendingRequests()) {
      throw BusinessException.tooMany(ErrorCode.TOO_MANY_PENDING, "You can have at most " + s.maxPendingRequests() + " pending requests");
    }
    if (active >= s.maxActiveLoans()) {
      throw BusinessException.tooMany(ErrorCode.TOO_MANY_ACTIVE_LOANS, "You can have at most " + s.maxActiveLoans() + " active loans");
    }
    if (hasBlockingOverlap(itemId, input.startDate(), input.endDate(), null)) throw BusinessException.datesUnavailable();
    if (loans.countDuplicatePending(itemId, user.id(), input.startDate(), input.endDate()) > 0) {
      throw conflict(ErrorCode.DUPLICATE_REQUEST, "You already have a pending request for these dates");
    }

    Loan loan = new Loan();
    loan.itemId = itemId;
    loan.borrowerId = user.id();
    loan.status = LoanStatus.PENDING;
    loan.startDate = input.startDate();
    loan.endDate = input.endDate();
    loan.originalEndDate = input.endDate();
    loan.message = input.message();
    loans.saveAndFlush(loan);

    User borrower = users.findById(user.id()).orElseThrow();
    notifications.notify(Notify.of(item.ownerId, NotificationType.LOAN_REQUESTED, "New request for " + item.name,
        borrower.name + " wants to borrow " + item.name + " from " + input.startDate() + " to " + input.endDate() + ".", link(loan.id)));
    return views.present(loan, user);
  }

  // ---------------------------------------------------------------- owner decisions

  /** PENDING -> APPROVED. Auto-rejects other pending requests whose dates overlap. */
  @Transactional
  public LoanView approve(AuthUser user, String loanId) {
    LocalDate today = clock.today();
    LoanContext c = loadForUpdate(loanId);
    Loan loan = c.loan();
    requireRole(c, user, ViewerRole.OWNER);
    if (loan.status != LoanStatus.PENDING) throw invalidTransition(loan.status, LoanEvent.APPROVE);
    if (c.item().isPaused || c.item().isRemoved || c.item().deletedAt != null) {
      throw conflict(ErrorCode.ITEM_UNAVAILABLE, "Unpause the item before approving requests");
    }
    if (loan.startDate.isBefore(today)) throw unprocessable(ErrorCode.INVALID_DATES, "The requested start date has already passed");
    if (c.borrower().status != UserStatus.ACTIVE || c.borrower().isDeleted()) {
      throw conflict(ErrorCode.INVALID_TRANSITION, "The borrower can no longer borrow items");
    }
    lockBorrower(loan.borrowerId);
    Settings s = settings.get();
    long active = loans.countByBorrowerIdAndStatusIn(loan.borrowerId, ACTIVE_FOR_LIMIT);
    long overdue = loans.countByBorrowerIdAndStatus(loan.borrowerId, LoanStatus.OVERDUE);
    if (overdue > 0) throw conflict(ErrorCode.OVERDUE_LOAN_EXISTS, "The borrower has an overdue loan");
    if (active >= s.maxActiveLoans()) {
      throw conflict(ErrorCode.TOO_MANY_ACTIVE_LOANS, "The borrower already has the maximum number of active loans");
    }
    if (hasBlockingOverlap(loan.itemId, loan.startDate, loan.endDate, loan.id)) throw BusinessException.datesUnavailable();

    loan.approvedAt = Instant.now();
    transition(loan, LoanEvent.APPROVE, Actor.OWNER);

    autoRejectOverlapping(loan.itemId, loan.id, loan.startDate, loan.endDate, c.item().name);
    notifications.notify(Notify.of(loan.borrowerId, NotificationType.LOAN_APPROVED, "Request approved: " + c.item().name,
        "Your request for " + c.item().name + " (" + loan.startDate + " to " + loan.endDate
            + ") was approved. Contact details are now visible on the loan page.",
        link(loan.id)));
    return reload(loan.id, user);
  }

  private void autoRejectOverlapping(String itemId, String exceptLoanId, LocalDate start, LocalDate end, String itemName) {
    List<Loan> overlapping = loans.findOverlappingPending(itemId, exceptLoanId, start, end);
    List<String> moved = bulkTransition(overlapping, LoanEvent.REJECT, BulkPatch.reject("Another request was approved for these dates"));
    for (Loan l : overlapping) {
      if (!moved.contains(l.id)) continue;
      notifications.notify(Notify.of(l.borrowerId, NotificationType.LOAN_REJECTED, "Request declined: " + itemName,
          itemName + " has been booked by someone else for overlapping dates.", link(l.id)));
    }
  }

  /** PENDING -> REJECTED (owner, with reason). */
  @Transactional
  public LoanView reject(AuthUser user, String loanId, String reason) {
    LoanContext c = loadForUpdate(loanId);
    Loan loan = c.loan();
    requireRole(c, user, ViewerRole.OWNER);
    loan.rejectionReason = reason;
    transition(loan, LoanEvent.REJECT, Actor.OWNER);
    notifications.notify(Notify.of(loan.borrowerId, NotificationType.LOAN_REJECTED, "Request declined: " + c.item().name,
        "Your request for " + c.item().name + " was declined. Reason: " + reason, link(loan.id)));
    return reload(loan.id, user);
  }

  /** PENDING -> CANCELLED (borrower) or APPROVED -> CANCELLED (either party), before handover. */
  @Transactional
  public LoanView cancel(AuthUser user, String loanId, String reason) {
    LoanContext c = loadForUpdate(loanId);
    Loan loan = c.loan();
    ViewerRole role = requireRole(c, user, ViewerRole.OWNER, ViewerRole.BORROWER);
    if (loan.status == LoanStatus.PENDING && role == ViewerRole.OWNER) {
      throw forbidden("Owners reject pending requests instead of cancelling them");
    }
    if (LoanStateMachine.next(loan.status, LoanEvent.CANCEL).isEmpty()) throw invalidTransition(loan.status, LoanEvent.CANCEL);
    loan.cancellationReason = reason;
    loan.cancelledById = user.id();
    transition(loan, LoanEvent.CANCEL, actorOf(role));
    String other = role == ViewerRole.OWNER ? loan.borrowerId : c.item().ownerId;
    String who = role == ViewerRole.OWNER ? "owner" : "borrower";
    notifications.notify(Notify.of(other, NotificationType.LOAN_CANCELLED, "Loan cancelled: " + c.item().name,
        "The " + who + " cancelled the loan of " + c.item().name + (reason != null ? ". Reason: " + reason : "."), link(loan.id)));
    return reload(loan.id, user);
  }

  // ---------------------------------------------------------------- two-step handover / return

  /** FR-17: owner marks handed over, borrower confirms receipt (APPROVED -> ACTIVE). */
  @Transactional
  public LoanView handover(AuthUser user, String loanId) {
    LocalDate today = clock.today();
    LoanContext c = loadForUpdate(loanId);
    Loan loan = c.loan();
    ViewerRole role = requireRole(c, user, ViewerRole.OWNER, ViewerRole.BORROWER);
    if (loan.status != LoanStatus.APPROVED) throw invalidTransition(loan.status, LoanEvent.CONFIRM_HANDOVER);
    if (loan.startDate.isAfter(today)) {
      throw unprocessable(ErrorCode.INVALID_DATES, "Handover is possible from the start date (" + loan.startDate + ")");
    }
    if (role == ViewerRole.OWNER) {
      if (loan.handoverMarkedAt != null) throw conflict(ErrorCode.ALREADY_MARKED, "Handover is already marked; waiting for the borrower");
      loan.handoverMarkedAt = Instant.now();
      loans.saveAndFlush(loan);
      notifications.notify(Notify.of(loan.borrowerId, NotificationType.LOAN_HANDOVER, "Confirm you received " + c.item().name,
          "The owner marked " + c.item().name + " as handed over. Please confirm receipt.", link(loan.id)));
    } else {
      if (loan.handoverMarkedAt == null) {
        throw conflict(ErrorCode.INVALID_TRANSITION, "The owner has to mark the item as handed over first");
      }
      loan.handedOverAt = Instant.now();
      transition(loan, LoanEvent.CONFIRM_HANDOVER, Actor.BORROWER);
      notifications.notify(Notify.of(c.item().ownerId, NotificationType.LOAN_HANDOVER, "Handover confirmed: " + c.item().name,
          c.borrower().name + " confirmed receiving " + c.item().name + ". It is due back on " + loan.endDate + ".", link(loan.id)));
    }
    return reload(loan.id, user);
  }

  /** FR-18: one party marks returned, the other confirms (ACTIVE|OVERDUE -> RETURNED). */
  @Transactional
  public LoanView markReturned(AuthUser user, String loanId, String conditionNote) {
    LoanContext c = loadForUpdate(loanId);
    Loan loan = c.loan();
    ViewerRole role = requireRole(c, user, ViewerRole.OWNER, ViewerRole.BORROWER);
    if (loan.status != LoanStatus.ACTIVE && loan.status != LoanStatus.OVERDUE) {
      throw invalidTransition(loan.status, LoanEvent.CONFIRM_RETURN);
    }
    boolean ownerNote = role == ViewerRole.OWNER && conditionNote != null;
    String other = role == ViewerRole.OWNER ? loan.borrowerId : c.item().ownerId;
    String name = c.item().name;

    if (loan.returnMarkedById == null) {
      loan.returnMarkedById = user.id();
      loan.returnMarkedAt = Instant.now();
      if (ownerNote) loan.returnConditionNote = conditionNote;
      loans.saveAndFlush(loan);
      notifications.notify(Notify.of(other, NotificationType.LOAN_RETURN, "Confirm return of " + name,
          "The " + (role == ViewerRole.OWNER ? "owner" : "borrower") + " marked " + name + " as returned. Please confirm.", link(loan.id)));
    } else if (loan.returnMarkedById.equals(user.id())) {
      throw conflict(ErrorCode.ALREADY_MARKED, "You already marked this item as returned; waiting for the other party");
    } else {
      loan.returnedAt = Instant.now();
      if (ownerNote) loan.returnConditionNote = conditionNote;
      transition(loan, LoanEvent.CONFIRM_RETURN, actorOf(role));
      notifications.notify(Notify.of(other, NotificationType.LOAN_RETURN, "Return confirmed: " + name,
          "The return of " + name + " is confirmed. You can now leave a review.", link(loan.id)));
    }
    return reload(loan.id, user);
  }

  // ---------------------------------------------------------------- extension (Rule 8)

  /** Rule 8: the borrower may request one extension. */
  @Transactional
  public LoanView requestExtension(AuthUser user, String loanId, LocalDate until, String message) {
    LoanContext c = loadForUpdate(loanId);
    Loan loan = c.loan();
    requireRole(c, user, ViewerRole.BORROWER);
    if (loan.status != LoanStatus.APPROVED && loan.status != LoanStatus.ACTIVE) {
      throw conflict(ErrorCode.INVALID_TRANSITION, "Extensions cannot be requested for a loan that is " + loan.status);
    }
    if (loan.extensionStatus != ExtensionStatus.NONE) {
      throw conflict(ErrorCode.EXTENSION_ALREADY_USED, "Only one extension can be requested per loan");
    }
    if (!until.isAfter(loan.endDate)) throw unprocessable(ErrorCode.INVALID_DATES, "The new end date must be after the current end date");
    if (inclusiveDays(loan.endDate.plusDays(1), until) > c.item().maxLoanDays) {
      throw unprocessable(ErrorCode.EXCEEDS_MAX_DURATION, "An extension can add at most " + c.item().maxLoanDays + " days");
    }
    if (hasBlockingOverlap(loan.itemId, loan.endDate.plusDays(1), until, loan.id)) throw BusinessException.datesUnavailable();

    loan.extensionStatus = ExtensionStatus.REQUESTED;
    loan.extensionRequestedUntil = until;
    loan.extensionMessage = message;
    loans.saveAndFlush(loan);
    notifications.notify(Notify.of(c.item().ownerId, NotificationType.LOAN_EXTENSION, "Extension requested: " + c.item().name,
        c.borrower().name + " asked to keep " + c.item().name + " until " + until + "." + (message != null ? " Message: " + message : ""),
        link(loan.id)));
    return reload(loan.id, user);
  }

  /** Owner accepts or declines; accepting must not collide with another approved loan. */
  @Transactional
  public LoanView decideExtension(AuthUser user, String loanId, boolean approve) {
    LocalDate today = clock.today();
    LoanContext c = loadForUpdate(loanId);
    Loan loan = c.loan();
    requireRole(c, user, ViewerRole.OWNER);
    if (loan.extensionStatus != ExtensionStatus.REQUESTED || loan.extensionRequestedUntil == null) {
      throw conflict(ErrorCode.NO_PENDING_EXTENSION, "There is no pending extension request");
    }
    if (!loan.status.isBlocking()) {
      throw conflict(ErrorCode.INVALID_TRANSITION, "Extensions cannot be decided for a loan that is " + loan.status);
    }
    LocalDate until = loan.extensionRequestedUntil;
    LocalDate oldEnd = loan.endDate;
    String name = c.item().name;

    if (approve) {
      if (hasBlockingOverlap(loan.itemId, oldEnd.plusDays(1), until, loan.id)) throw BusinessException.datesUnavailable();
      loan.endDate = until;
      loan.extensionStatus = ExtensionStatus.APPROVED;
      if (loan.status == LoanStatus.OVERDUE && !until.isBefore(today)) {
        transition(loan, LoanEvent.EXTEND_OVERDUE, Actor.OWNER);
      } else {
        loans.saveAndFlush(loan);
      }
      autoRejectOverlapping(loan.itemId, loan.id, oldEnd.plusDays(1), until, name);
    } else {
      loan.extensionStatus = ExtensionStatus.DECLINED;
      loans.saveAndFlush(loan);
    }
    notifications.notify(Notify.of(loan.borrowerId, NotificationType.LOAN_EXTENSION,
        "Extension " + (approve ? "approved" : "declined") + ": " + name,
        approve
            ? "You can keep " + name + " until " + until + "."
            : "Your extension request for " + name + " was declined. It is still due on " + oldEnd + ".",
        link(loan.id)));
    return reload(loan.id, user);
  }

  // ---------------------------------------------------------------- reviews

  /** RETURNED -> COMPLETED once both parties have reviewed. Called by the review service. */
  @Transactional
  public void completeIfBothReviewed(String loanId) {
    LoanContext c = views.context(loans.findById(loanId).orElseThrow());
    Loan loan = c.loan();
    if (loan.status == LoanStatus.RETURNED && c.reviewerIds().contains(loan.borrowerId) && c.reviewerIds().contains(c.item().ownerId)) {
      loan.completedAt = Instant.now();
      transition(loan, LoanEvent.COMPLETE, Actor.SYSTEM);
    }
  }

  // ---------------------------------------------------------------- cascades (item deletion, takedown, account deletion)

  @Transactional
  public void rejectPendingForItem(String itemId, String reason) {
    List<Loan> pending = loans.findByItemIdAndStatus(itemId, LoanStatus.PENDING);
    List<String> moved = bulkTransition(pending, LoanEvent.REJECT, BulkPatch.reject(reason));
    if (moved.isEmpty()) return;
    String name = items.findById(itemId).map(i -> i.name).orElse("the item");
    for (Loan l : pending) {
      if (!moved.contains(l.id)) continue;
      notifications.notify(Notify.of(l.borrowerId, NotificationType.LOAN_REJECTED, "Request declined: " + name,
          "Your request for " + name + " was declined. Reason: " + reason, link(l.id)));
    }
  }

  @Transactional
  public void cancelApprovedForItem(String itemId, String reason, String actorId) {
    List<Loan> approved = loans.findByItemIdAndStatus(itemId, LoanStatus.APPROVED);
    List<String> moved = bulkTransition(approved, LoanEvent.CANCEL, BulkPatch.cancel(reason, actorId));
    if (moved.isEmpty()) return;
    Item item = items.findById(itemId).orElseThrow();
    for (Loan l : approved) {
      if (!moved.contains(l.id)) continue;
      for (String userId : List.of(l.borrowerId, item.ownerId)) {
        notifications.notify(Notify.of(userId, NotificationType.LOAN_CANCELLED, "Loan cancelled: " + item.name,
            "The loan of " + item.name + " was cancelled. Reason: " + reason, link(l.id)));
      }
    }
  }

  /** Account deletion: own pending requests are cancelled; pending requests on own items are rejected. */
  @Transactional
  public void closePendingForDeletedUser(String userId) {
    bulkTransition(loans.findByBorrowerIdAndStatus(userId, LoanStatus.PENDING), LoanEvent.CANCEL,
        BulkPatch.cancel("Account deleted", userId));
    for (Item item : items.findByOwnerId(userId)) rejectPendingForItem(item.id, "The owner closed their account");
  }

  // ---------------------------------------------------------------- scheduled transitions (Section 11)

  /** PENDING -> EXPIRED when the owner has not responded within request_expiry_hours (or the start date passed). */
  @Transactional
  public int expirePending(Instant now) {
    Settings s = settings.get();
    Instant cutoff = now.minus(Duration.ofHours(s.requestExpiryHours()));
    List<Loan> candidates = loans.findExpirable(cutoff, clock.today(now));
    List<String> moved = bulkTransition(candidates, LoanEvent.EXPIRE, BulkPatch.NONE);
    Map<String, Item> itemMap = itemsById(candidates);
    for (Loan l : candidates) {
      if (!moved.contains(l.id)) continue;
      String name = itemMap.get(l.itemId).name;
      notifications.notify(Notify.of(l.borrowerId, NotificationType.LOAN_EXPIRED, "Request expired: " + name,
          "The owner did not respond to your request for " + name + " in time, so it expired.", link(l.id)).dedupe("expired:" + l.id));
    }
    return moved.size();
  }

  /** ACTIVE -> OVERDUE when end_date is before today. */
  @Transactional
  public int markOverdue(Instant now) {
    return bulkTransition(loans.findByStatusAndEndDateBefore(LoanStatus.ACTIVE, clock.today(now)), LoanEvent.MARK_OVERDUE, BulkPatch.NONE).size();
  }

  /** RETURNED -> COMPLETED after auto_complete_days. */
  @Transactional
  public int autoComplete(Instant now) {
    Settings s = settings.get();
    Instant cutoff = now.minus(Duration.ofDays(s.autoCompleteDays()));
    return bulkTransition(loans.findByStatusAndReturnedAtBefore(LoanStatus.RETURNED, cutoff), LoanEvent.COMPLETE,
        new BulkPatch(null, null, null, now)).size();
  }

  private Map<String, Item> itemsById(Collection<Loan> list) {
    return items.findByIdIn(list.stream().map(l -> l.itemId).collect(Collectors.toSet())).stream()
        .collect(Collectors.toMap(i -> i.id, Function.identity()));
  }
}
