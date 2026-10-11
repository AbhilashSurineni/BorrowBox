package com.borrowbox.loans;

import com.borrowbox.common.security.AuthUser;
import com.borrowbox.items.Item;
import com.borrowbox.items.ItemImageRepository;
import com.borrowbox.items.ItemRepository;
import com.borrowbox.reviews.Review;
import com.borrowbox.reviews.ReviewRepository;
import com.borrowbox.users.User;
import com.borrowbox.users.UserRepository;
import com.borrowbox.users.UserViews;
import com.borrowbox.users.UserViews.Contact;
import com.borrowbox.users.UserViews.PublicUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Builds the loan response shape (same fields as the TS presentLoan), batch-loading relations. */
@Component
public class LoanViews {
  public enum ViewerRole { BORROWER, OWNER, ADMIN }

  public record Extension(ExtensionStatus status, LocalDate requestedUntil, String message) {}

  public record LoanItem(String id, String name, String pickupLocation, int maxLoanDays, String coverImageUrl) {}

  public record ReviewedBy(boolean borrower, boolean owner) {}

  public record LoanView(
      String id,
      LoanStatus status,
      String itemId,
      LocalDate startDate,
      LocalDate endDate,
      LocalDate originalEndDate,
      String message,
      String rejectionReason,
      String cancellationReason,
      String cancelledBy,
      Instant approvedAt,
      Instant handoverMarkedAt,
      Instant handedOverAt,
      String returnMarkedBy,
      Instant returnMarkedAt,
      Instant returnedAt,
      String returnConditionNote,
      Extension extension,
      Instant completedAt,
      Instant createdAt,
      Instant updatedAt,
      LoanItem item,
      PublicUser owner,
      PublicUser borrower,
      ViewerRole viewerRole,
      Contact counterpartyContact,
      ReviewedBy reviewedBy,
      List<String> availableActions) {}

  /** A loan with everything needed to present it or apply business rules. */
  public record LoanContext(Loan loan, Item item, User owner, User borrower, String coverImageUrl, Set<String> reviewerIds) {}

  private final ItemRepository items;
  private final UserRepository users;
  private final ItemImageRepository images;
  private final ReviewRepository reviews;

  public LoanViews(ItemRepository items, UserRepository users, ItemImageRepository images, ReviewRepository reviews) {
    this.items = items;
    this.users = users;
    this.images = images;
    this.reviews = reviews;
  }

  public List<LoanContext> contexts(Collection<Loan> loans) {
    if (loans.isEmpty()) return List.of();
    Set<String> itemIds = loans.stream().map(l -> l.itemId).collect(Collectors.toSet());
    Map<String, Item> itemMap = items.findByIdIn(itemIds).stream().collect(Collectors.toMap(i -> i.id, Function.identity()));
    Set<String> userIds = new HashSet<>();
    loans.forEach(l -> userIds.add(l.borrowerId));
    itemMap.values().forEach(i -> userIds.add(i.ownerId));
    Map<String, User> userMap = users.findByIdIn(userIds).stream().collect(Collectors.toMap(u -> u.id, Function.identity()));
    Map<String, String> covers = new HashMap<>();
    for (Object[] row : images.findCovers(itemIds)) covers.putIfAbsent((String) row[0], (String) row[1]);
    Map<String, Set<String>> reviewers = new HashMap<>();
    for (Review r : reviews.findByLoanIdIn(loans.stream().map(l -> l.id).toList())) {
      reviewers.computeIfAbsent(r.loanId, k -> new HashSet<>()).add(r.reviewerId);
    }
    List<LoanContext> out = new ArrayList<>();
    for (Loan l : loans) {
      Item item = itemMap.get(l.itemId);
      out.add(new LoanContext(l, item, userMap.get(item.ownerId), userMap.get(l.borrowerId), covers.get(l.itemId),
          reviewers.getOrDefault(l.id, Set.of())));
    }
    return out;
  }

  public LoanContext context(Loan loan) {
    return contexts(List.of(loan)).get(0);
  }

  public static ViewerRole roleOf(LoanContext c, AuthUser user) {
    if (c.loan().borrowerId.equals(user.id())) return ViewerRole.BORROWER;
    if (c.item().ownerId.equals(user.id())) return ViewerRole.OWNER;
    if (user.isAdmin()) return ViewerRole.ADMIN;
    return null;
  }

  public LoanView present(Loan loan, AuthUser viewer) {
    return present(context(loan), viewer);
  }

  public List<LoanView> present(Collection<Loan> loans, AuthUser viewer) {
    return contexts(loans).stream().map(c -> present(c, viewer)).toList();
  }

  public static LoanView present(LoanContext c, AuthUser viewer) {
    Loan l = c.loan();
    ViewerRole role = roleOf(c, viewer);
    boolean party = role == ViewerRole.OWNER || role == ViewerRole.BORROWER;
    boolean contactVisible = LoanStatus.CONTACT_VISIBLE.contains(l.status) && party;
    User counterparty = role == ViewerRole.OWNER ? c.borrower() : c.owner();
    return new LoanView(
        l.id,
        l.status,
        l.itemId,
        l.startDate,
        l.endDate,
        l.originalEndDate,
        l.message,
        l.rejectionReason,
        l.cancellationReason,
        l.cancelledById == null ? null : (l.cancelledById.equals(l.borrowerId) ? "BORROWER" : "OWNER"),
        l.approvedAt,
        l.handoverMarkedAt,
        l.handedOverAt,
        l.returnMarkedById == null ? null : (l.returnMarkedById.equals(l.borrowerId) ? "BORROWER" : "OWNER"),
        l.returnMarkedAt,
        l.returnedAt,
        l.returnConditionNote,
        new Extension(l.extensionStatus, l.extensionRequestedUntil, l.extensionMessage),
        l.completedAt,
        l.createdAt,
        l.updatedAt,
        new LoanItem(c.item().id, c.item().name, c.item().pickupLocation, c.item().maxLoanDays, c.coverImageUrl()),
        UserViews.toPublic(c.owner()),
        UserViews.toPublic(c.borrower()),
        role,
        // Privacy: contact details only for the counterparty, and only once approved.
        contactVisible ? UserViews.toContact(counterparty) : null,
        new ReviewedBy(c.reviewerIds().contains(l.borrowerId), c.reviewerIds().contains(c.item().ownerId)),
        availableActions(c, role));
  }

  /** Same rules as the TS availableActions(). */
  static List<String> availableActions(LoanContext c, ViewerRole role) {
    List<String> actions = new ArrayList<>();
    if (role == null || role == ViewerRole.ADMIN) return actions;
    Loan l = c.loan();
    boolean isOwner = role == ViewerRole.OWNER;
    String viewerId = isOwner ? c.item().ownerId : l.borrowerId;
    boolean reviewed = c.reviewerIds().contains(viewerId);
    switch (l.status) {
      case PENDING -> actions.addAll(isOwner ? List.of("approve", "reject") : List.of("cancel"));
      case APPROVED -> {
        actions.add("cancel");
        if (isOwner && l.handoverMarkedAt == null) actions.add("handover");
        if (!isOwner && l.handoverMarkedAt != null) actions.add("confirm_handover");
        if (!isOwner && l.extensionStatus == ExtensionStatus.NONE) actions.add("request_extension");
      }
      case ACTIVE, OVERDUE -> {
        if (l.returnMarkedById == null) actions.add("mark_returned");
        else if (!l.returnMarkedById.equals(viewerId)) actions.add("confirm_return");
        if (!isOwner && l.status == LoanStatus.ACTIVE && l.extensionStatus == ExtensionStatus.NONE) actions.add("request_extension");
      }
      case RETURNED, COMPLETED -> {
        if (!reviewed) actions.add("review");
      }
      default -> {}
    }
    if (isOwner && l.extensionStatus == ExtensionStatus.REQUESTED && l.status.isBlocking()) actions.add("decide_extension");
    return actions;
  }
}
