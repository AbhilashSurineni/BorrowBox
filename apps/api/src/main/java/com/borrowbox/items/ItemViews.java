package com.borrowbox.items;

import com.borrowbox.loans.Loan;
import com.borrowbox.loans.LoanStatus;
import com.borrowbox.users.UserViews.PublicUser;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Response shapes for items (same fields as the TS presenters). */
public final class ItemViews {
  private ItemViews() {}

  public record CategoryRef(String id, String name, String icon) {}

  public record ImageView(String id, String url, int position) {}

  public record BlockedRange(LocalDate startDate, LocalDate endDate, String status) {}

  public record ItemReview(String id, int rating, String comment, Instant createdAt, PublicUser reviewer) {}

  public record ItemView(
      String id,
      String name,
      String description,
      ItemCondition condition,
      int maxLoanDays,
      String pickupLocation,
      String depositNote,
      boolean isPaused,
      boolean isRemoved,
      double ratingAvg,
      int ratingCount,
      ItemStatus status,
      CategoryRef category,
      List<ImageView> images,
      PublicUser owner,
      Instant createdAt,
      Instant updatedAt) {}

  public record ItemDetail(
      String id,
      String name,
      String description,
      ItemCondition condition,
      int maxLoanDays,
      String pickupLocation,
      String depositNote,
      boolean isPaused,
      boolean isRemoved,
      double ratingAvg,
      int ratingCount,
      ItemStatus status,
      CategoryRef category,
      List<ImageView> images,
      PublicUser owner,
      Instant createdAt,
      Instant updatedAt,
      @JsonInclude(JsonInclude.Include.NON_NULL) String removedReason,
      List<BlockedRange> availability,
      List<ItemReview> reviews) {}

  public record Upcoming(String loanId, LoanStatus status, LocalDate startDate, LocalDate endDate, PublicUser borrower, Instant createdAt) {}

  public record MyItem(
      String id,
      String name,
      String description,
      ItemCondition condition,
      int maxLoanDays,
      String pickupLocation,
      String depositNote,
      boolean isPaused,
      boolean isRemoved,
      double ratingAvg,
      int ratingCount,
      ItemStatus status,
      CategoryRef category,
      List<ImageView> images,
      PublicUser owner,
      Instant createdAt,
      Instant updatedAt,
      String removedReason,
      int pendingRequestCount,
      List<Upcoming> upcoming) {}

  public record Availability(String itemId, ItemStatus status, int maxLoanDays, List<BlockedRange> blocked) {}

  public record ItemSummary(
      String id,
      String name,
      String description,
      ItemCondition condition,
      int maxLoanDays,
      String pickupLocation,
      double ratingAvg,
      int ratingCount,
      String coverImageUrl,
      ItemStatus status,
      LocalDate nextAvailableDate,
      CategoryRef category,
      PublicUser owner,
      Instant createdAt) {}

  /** FR-20: item status derived from its blocking loans. */
  public static ItemStatus deriveStatus(Item item, Collection<Loan> blockingLoans, LocalDate today) {
    if (item.isRemoved || item.deletedAt != null) return ItemStatus.REMOVED;
    if (item.isPaused) return ItemStatus.PAUSED;
    if (blockingLoans.stream().anyMatch(l -> l.status == LoanStatus.ACTIVE || l.status == LoanStatus.OVERDUE)) return ItemStatus.ON_LOAN;
    if (blockingLoans.stream().anyMatch(l -> l.status == LoanStatus.APPROVED && !l.endDate.isBefore(today))) return ItemStatus.RESERVED;
    return ItemStatus.AVAILABLE;
  }

  /** Blocked ranges for the availability calendar. Overdue loans stay blocked until today. */
  public static List<BlockedRange> blockedRanges(Collection<Loan> loans, LocalDate today) {
    return loans.stream()
        .map(l -> new BlockedRange(
            l.startDate,
            l.status == LoanStatus.OVERDUE && l.endDate.isBefore(today) ? today : l.endDate,
            l.status == LoanStatus.APPROVED ? "RESERVED" : "ON_LOAN"))
        .filter(r -> !r.endDate().isBefore(today))
        .sorted(Comparator.comparing(BlockedRange::startDate))
        .toList();
  }
}
