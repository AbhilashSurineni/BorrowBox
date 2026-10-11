package com.borrowbox.reviews;

import com.borrowbox.common.errors.BusinessException;
import com.borrowbox.common.errors.ErrorCode;
import com.borrowbox.common.security.AuthUser;
import com.borrowbox.items.Item;
import com.borrowbox.items.ItemRepository;
import com.borrowbox.loans.Loan;
import com.borrowbox.loans.LoanRepository;
import com.borrowbox.loans.LoanService;
import com.borrowbox.loans.LoanStatus;
import com.borrowbox.notifications.NotificationService;
import com.borrowbox.notifications.NotificationService.Notify;
import com.borrowbox.notifications.NotificationType;
import com.borrowbox.users.User;
import com.borrowbox.users.UserRepository;
import com.borrowbox.users.UserViews;
import com.borrowbox.users.UserViews.PublicUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
  private final ReviewRepository reviews;
  private final LoanRepository loans;
  private final ItemRepository items;
  private final UserRepository users;
  private final LoanService loanService;
  private final NotificationService notifications;

  public ReviewService(
      ReviewRepository reviews,
      LoanRepository loans,
      ItemRepository items,
      UserRepository users,
      LoanService loanService,
      NotificationService notifications) {
    this.reviews = reviews;
    this.loans = loans;
    this.items = items;
    this.users = users;
    this.loanService = loanService;
    this.notifications = notifications;
  }

  public record CreatedReview(String id, String loanId, int rating, String comment, String revieweeId, PublicUser reviewer, Instant createdAt) {}

  /** Average of 1–5 ratings rounded to 2 decimals (numeric(3,2)); 0 when there are none. */
  public static BigDecimal ratingAverage(double avg) {
    return BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
  }

  /** FR-25 / Rule 6: after a returned loan, each party may review the other once. */
  @Transactional
  public CreatedReview create(AuthUser user, String loanId, int rating, String comment) {
    Loan loan = loans.findById(loanId).orElseThrow(() -> BusinessException.notFound("Loan"));
    Item item = items.findById(loan.itemId).orElseThrow();
    boolean isBorrower = loan.borrowerId.equals(user.id());
    boolean isOwner = item.ownerId.equals(user.id());
    if (!isBorrower && !isOwner) throw BusinessException.forbidden("Only the parties to this loan can review it");
    if (loan.status != LoanStatus.RETURNED && loan.status != LoanStatus.COMPLETED) {
      throw BusinessException.conflict(ErrorCode.REVIEW_NOT_ALLOWED, "Reviews open once the item has been returned");
    }
    if (reviews.findByLoanIdAndReviewerId(loanId, user.id()).isPresent()) throw reviewExists();

    String revieweeId = isBorrower ? item.ownerId : loan.borrowerId;
    Review review = new Review();
    review.loanId = loanId;
    review.reviewerId = user.id();
    review.revieweeId = revieweeId;
    review.rating = rating;
    review.comment = comment;
    try {
      reviews.saveAndFlush(review);
    } catch (DataIntegrityViolationException e) {
      // unique (loan_id, reviewer_id) is the backstop for concurrent double submissions
      throw reviewExists();
    }
    refreshUserRating(revieweeId);
    if (isBorrower) refreshItemRating(item);
    loanService.completeIfBothReviewed(loanId);

    User reviewer = users.findById(user.id()).orElseThrow();
    notifications.notify(Notify.of(revieweeId, NotificationType.REVIEW_RECEIVED, "You received a new review",
        reviewer.name + " rated you " + rating + "/5 for the loan of " + item.name + ".", "/loans/" + loanId));
    return new CreatedReview(review.id, loanId, rating, comment, revieweeId, UserViews.toPublic(reviewer), review.createdAt);
  }

  private static BusinessException reviewExists() {
    return BusinessException.conflict(ErrorCode.REVIEW_EXISTS, "You have already reviewed this loan");
  }

  /** Denormalised user rating over all reviews received. */
  private void refreshUserRating(String userId) {
    Object[] agg = reviews.aggregateForUser(userId).get(0);
    User u = users.findById(userId).orElseThrow();
    u.ratingAvg = ratingAverage(((Number) agg[0]).doubleValue());
    u.ratingCount = ((Number) agg[1]).intValue();
    users.save(u);
  }

  /** Item rating = borrowers' reviews of the owner on loans of this item. */
  private void refreshItemRating(Item item) {
    Object[] agg = reviews.aggregateForItem(item.id, item.ownerId).get(0);
    item.ratingAvg = ratingAverage(((Number) agg[0]).doubleValue());
    item.ratingCount = ((Number) agg[1]).intValue();
    items.save(item);
  }
}
