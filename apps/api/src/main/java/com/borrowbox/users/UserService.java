package com.borrowbox.users;

import com.borrowbox.auth.RefreshTokenRepository;
import com.borrowbox.common.config.AfterCommit;
import com.borrowbox.common.errors.BusinessException;
import com.borrowbox.common.errors.ErrorCode;
import com.borrowbox.common.security.TokenHashing;
import com.borrowbox.common.storage.ImageService;
import com.borrowbox.common.web.PageQuery;
import com.borrowbox.common.web.Paged;
import com.borrowbox.items.Item;
import com.borrowbox.items.ItemRepository;
import com.borrowbox.loans.Loan;
import com.borrowbox.loans.LoanRepository;
import com.borrowbox.loans.LoanService;
import com.borrowbox.loans.LoanStatus;
import com.borrowbox.reviews.Review;
import com.borrowbox.reviews.ReviewRepository;
import com.borrowbox.users.UserViews.PrivateUser;
import com.borrowbox.users.UserViews.PublicUser;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {
  private static final List<LoanStatus> COMPLETED = List.of(LoanStatus.RETURNED, LoanStatus.COMPLETED);

  private final UserRepository users;
  private final ItemRepository items;
  private final LoanRepository loans;
  private final ReviewRepository reviews;
  private final RefreshTokenRepository refreshTokens;
  private final LoanService loanService;
  private final ImageService imageService;
  private final PasswordEncoder encoder;

  public UserService(
      UserRepository users,
      ItemRepository items,
      LoanRepository loans,
      ReviewRepository reviews,
      RefreshTokenRepository refreshTokens,
      LoanService loanService,
      ImageService imageService,
      PasswordEncoder encoder) {
    this.users = users;
    this.items = items;
    this.loans = loans;
    this.reviews = reviews;
    this.refreshTokens = refreshTokens;
    this.loanService = loanService;
    this.imageService = imageService;
    this.encoder = encoder;
  }

  public record ReviewItem(String id, String name) {}

  public record ReviewView(
      String id, String loanId, int rating, String comment, Instant createdAt, PublicUser reviewer, String reviewerRole, ReviewItem item) {}

  public record Stats(long itemsListed, long completedLoans, long completedAsBorrower, long completedAsLender) {}

  public record PublicProfile(
      String id,
      String name,
      String department,
      String avatarUrl,
      double ratingAvg,
      int ratingCount,
      Instant memberSince,
      Stats stats,
      List<ReviewView> recentReviews) {}

  /** PATCH /users/me: absent fields stay unchanged; phone '' or null clears it. */
  public record UpdateProfile(UserRequests.UpdateProfileRequest fields, boolean clearPhone, boolean removeAvatar) {}

  @Transactional(readOnly = true)
  public PrivateUser getMe(String userId) {
    User u = users.findById(userId).filter(x -> !x.isDeleted()).orElseThrow(() -> BusinessException.notFound("User"));
    return UserViews.toPrivate(u);
  }

  @Transactional
  public PrivateUser updateMe(String userId, UpdateProfile cmd, MultipartFile avatar) {
    User u = users.findById(userId).orElseThrow();
    String previousAvatar = u.avatarUrl;
    boolean avatarChanged = false;
    if (avatar != null) {
      ImageService.checkUpload(avatar);
      u.avatarUrl = imageService.store(avatar);
      avatarChanged = true;
    } else if (cmd.removeAvatar()) {
      u.avatarUrl = null;
      avatarChanged = true;
    }
    var in = cmd.fields();
    if (in.name() != null) u.name = in.name();
    if (in.department() != null) u.department = in.department();
    if (in.phone() != null) u.phone = in.phone();
    else if (cmd.clearPhone()) u.phone = null;
    if (in.contactPreference() != null) u.contactPreference = ContactPreference.valueOf(in.contactPreference());
    users.saveAndFlush(u);
    if (avatarChanged && previousAvatar != null) AfterCommit.run(() -> imageService.remove(previousAvatar));
    return UserViews.toPrivate(u);
  }

  /** FR-5: public profile with rating, items listed and completed loans. */
  @Transactional(readOnly = true)
  public PublicProfile getPublicProfile(String id) {
    User u = users.findById(id).filter(x -> !x.isDeleted()).orElseThrow(() -> BusinessException.notFound("User"));
    long itemsListed = items.countByOwnerIdAndIsRemovedFalseAndDeletedAtIsNull(id);
    long asBorrower = loans.countByBorrowerIdAndStatusIn(id, COMPLETED);
    long asLender = loans.countAsLender(id, COMPLETED);
    List<Review> recent = reviews.findByRevieweeIdOrderByCreatedAtDesc(id, PageRequest.of(0, 5)).getContent();
    return new PublicProfile(u.id, u.name, u.department, u.avatarUrl, u.ratingAvg.doubleValue(), u.ratingCount, u.createdAt,
        new Stats(itemsListed, asBorrower + asLender, asBorrower, asLender), presentReviews(recent));
  }

  @Transactional(readOnly = true)
  public Paged<ReviewView> listReviews(String userId, PageQuery q) {
    if (!users.existsById(userId)) throw BusinessException.notFound("User");
    Page<Review> page = reviews.findByRevieweeIdOrderByCreatedAtDesc(userId, q.pageable(Sort.unsorted()));
    return Paged.of(presentReviews(page.getContent()), page.getTotalElements(), q);
  }

  private List<ReviewView> presentReviews(List<Review> list) {
    if (list.isEmpty()) return List.of();
    Map<String, Loan> loanMap = loans.findByIdIn(list.stream().map(r -> r.loanId).collect(Collectors.toSet())).stream()
        .collect(Collectors.toMap(l -> l.id, Function.identity()));
    Map<String, Item> itemMap = items.findByIdIn(loanMap.values().stream().map(l -> l.itemId).collect(Collectors.toSet())).stream()
        .collect(Collectors.toMap(i -> i.id, Function.identity()));
    Map<String, User> reviewers = users.findByIdIn(list.stream().map(r -> r.reviewerId).collect(Collectors.toSet())).stream()
        .collect(Collectors.toMap(x -> x.id, Function.identity()));
    return list.stream().map(r -> {
      Loan loan = loanMap.get(r.loanId);
      Item item = itemMap.get(loan.itemId);
      return new ReviewView(r.id, r.loanId, r.rating, r.comment, r.createdAt, UserViews.toPublic(reviewers.get(r.reviewerId)),
          r.reviewerId.equals(loan.borrowerId) ? "BORROWER" : "OWNER", new ReviewItem(item.id, item.name));
    }).toList();
  }

  /**
   * Account deletion (blocked while the user has active loans as borrower or lender). The row is
   * anonymised rather than hard-deleted so loan history and reviews stay consistent.
   */
  @Transactional
  public void deleteMe(String userId) {
    if (loans.countBlockingForUser(userId) > 0) {
      throw BusinessException.conflict(ErrorCode.HAS_ACTIVE_LOANS,
          "Finish or cancel your approved and active loans before deleting your account");
    }
    loanService.closePendingForDeletedUser(userId);
    items.softDeleteByOwner(userId);
    refreshTokens.revokeAllForUser(userId, Instant.now());
    User u = users.findById(userId).orElseThrow();
    String avatar = u.avatarUrl;
    u.name = "Deleted user";
    u.email = "deleted+" + userId + "@deleted.invalid";
    u.passwordHash = encoder.encode(TokenHashing.randomToken());
    u.phone = null;
    u.avatarUrl = null;
    u.department = "-";
    u.deletedAt = Instant.now();
    users.save(u);
    if (avatar != null) AfterCommit.run(() -> imageService.remove(avatar));
  }

}
