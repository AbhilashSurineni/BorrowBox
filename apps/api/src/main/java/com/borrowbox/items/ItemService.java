package com.borrowbox.items;

import static com.borrowbox.common.errors.BusinessException.conflict;
import static com.borrowbox.common.errors.BusinessException.forbidden;
import static com.borrowbox.common.errors.BusinessException.notFound;
import static com.borrowbox.common.errors.BusinessException.unprocessable;

import com.borrowbox.common.config.AfterCommit;
import com.borrowbox.common.errors.ErrorCode;
import com.borrowbox.common.security.AuthUser;
import com.borrowbox.common.settings.SettingsService;
import com.borrowbox.common.storage.ImageService;
import com.borrowbox.common.time.CampusClock;
import com.borrowbox.common.web.PageQuery;
import com.borrowbox.common.web.Paged;
import com.borrowbox.items.ItemRequests.CreateItemRequest;
import com.borrowbox.items.ItemRequests.UpdateItemRequest;
import com.borrowbox.items.ItemViews.*;
import com.borrowbox.loans.Loan;
import com.borrowbox.loans.LoanRepository;
import com.borrowbox.loans.LoanService;
import com.borrowbox.loans.LoanStatus;
import com.borrowbox.reviews.Review;
import com.borrowbox.reviews.ReviewRepository;
import com.borrowbox.users.User;
import com.borrowbox.users.UserRepository;
import com.borrowbox.users.UserViews;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ItemService {
  private final ItemRepository items;
  private final ItemImageRepository images;
  private final CategoryRepository categories;
  private final UserRepository users;
  private final LoanRepository loans;
  private final ReviewRepository reviews;
  private final ItemSearchRepository search;
  private final LoanService loanService;
  private final SettingsService settings;
  private final ImageService imageService;
  private final CampusClock clock;

  public ItemService(
      ItemRepository items,
      ItemImageRepository images,
      CategoryRepository categories,
      UserRepository users,
      LoanRepository loans,
      ReviewRepository reviews,
      ItemSearchRepository search,
      LoanService loanService,
      SettingsService settings,
      ImageService imageService,
      CampusClock clock) {
    this.items = items;
    this.images = images;
    this.categories = categories;
    this.users = users;
    this.loans = loans;
    this.reviews = reviews;
    this.search = search;
    this.loanService = loanService;
    this.settings = settings;
    this.imageService = imageService;
    this.clock = clock;
  }

  /** PATCH semantics for fields that can be cleared ('' or null) vs left unchanged (absent). */
  public record UpdateItem(UpdateItemRequest fields, boolean clearDepositNote, List<String> removeImageIds) {}

  // ---------------------------------------------------------------- reads

  @Transactional(readOnly = true)
  public Paged<ItemSummary> list(ItemSearchRepository.Criteria criteria, PageQuery q) {
    var result = search.search(criteria, clock.today());
    return Paged.of(result.data(), result.total(), q);
  }

  /** FR-12: item detail with gallery, owner, availability and reviews. */
  @Transactional(readOnly = true)
  public ItemDetail getById(String id, AuthUser viewer) {
    LocalDate today = clock.today();
    Item item = items.findById(id).orElse(null);
    boolean privileged = viewer != null && item != null && (viewer.isAdmin() || viewer.id().equals(item.ownerId));
    if (item == null || item.deletedAt != null || (item.isRemoved && !privileged)) throw notFound("Item");
    List<Loan> blocking = loans.findRelevantBlocking(List.of(id), today);
    List<Review> itemReviews = reviews.findForItem(id, item.ownerId, PageRequest.of(0, 10));
    Map<String, User> reviewers = usersById(itemReviews.stream().map(r -> r.reviewerId).collect(Collectors.toSet()));
    ItemView v = present(item, blocking, today);
    return new ItemDetail(
        v.id(), v.name(), v.description(), v.condition(), v.maxLoanDays(), v.pickupLocation(), v.depositNote(), v.isPaused(),
        v.isRemoved(), v.ratingAvg(), v.ratingCount(), v.status(), v.category(), v.images(), v.owner(), v.createdAt(), v.updatedAt(),
        privileged && item.isRemoved ? item.removedReason : null,
        ItemViews.blockedRanges(blocking, today),
        itemReviews.stream()
            .map(r -> new ItemReview(r.id, r.rating, r.comment, r.createdAt, UserViews.toPublic(reviewers.get(r.reviewerId))))
            .toList());
  }

  @Transactional(readOnly = true)
  public Availability availability(String id) {
    LocalDate today = clock.today();
    Item item = items.findById(id).orElse(null);
    if (item == null || item.deletedAt != null || item.isRemoved) throw notFound("Item");
    List<Loan> blocking = loans.findRelevantBlocking(List.of(id), today);
    return new Availability(id, ItemViews.deriveStatus(item, blocking, today), item.maxLoanDays, ItemViews.blockedRanges(blocking, today));
  }

  /** FR-8: own listings with status and upcoming requests. */
  @Transactional(readOnly = true)
  public Paged<MyItem> listMine(String ownerId, String status, PageQuery q) {
    LocalDate today = clock.today();
    Page<Item> page = items.findMine(ownerId, status, q.pageable(Sort.by(Sort.Direction.DESC, "createdAt")));
    List<Item> rows = page.getContent();
    List<String> ids = rows.stream().map(i -> i.id).toList();
    List<Loan> upcoming = ids.isEmpty() ? List.of() : loans.findUpcomingForItems(ids, today);
    Map<String, User> borrowers = usersById(upcoming.stream().map(l -> l.borrowerId).collect(Collectors.toSet()));
    Map<String, List<Loan>> byItem = upcoming.stream().collect(Collectors.groupingBy(l -> l.itemId));
    Presenter presenter = presenter(rows);
    List<MyItem> data = rows.stream().map(item -> {
      List<Loan> itemLoans = byItem.getOrDefault(item.id, List.of());
      List<Loan> blocking = itemLoans.stream().filter(l -> l.status != LoanStatus.PENDING).toList();
      ItemView v = presenter.present(item, blocking, today);
      return new MyItem(
          v.id(), v.name(), v.description(), v.condition(), v.maxLoanDays(), v.pickupLocation(), v.depositNote(), v.isPaused(),
          v.isRemoved(), v.ratingAvg(), v.ratingCount(), v.status(), v.category(), v.images(), v.owner(), v.createdAt(), v.updatedAt(),
          item.removedReason,
          (int) itemLoans.stream().filter(l -> l.status == LoanStatus.PENDING).count(),
          itemLoans.stream()
              .map(l -> new Upcoming(l.id, l.status, l.startDate, l.endDate, UserViews.toPublic(borrowers.get(l.borrowerId)), l.createdAt))
              .toList());
    }).toList();
    return Paged.of(data, page.getTotalElements(), q);
  }

  // ---------------------------------------------------------------- writes

  @Transactional
  public ItemView create(String ownerId, CreateItemRequest input, List<MultipartFile> files) {
    if (files.size() > ImageService.MAX_ITEM_IMAGES) throw unprocessable(ErrorCode.TOO_MANY_IMAGES, "Up to 5 images are allowed");
    assertActiveCategory(input.categoryId());
    List<String> urls = new ArrayList<>();
    try {
      for (MultipartFile f : files) urls.add(imageService.store(f));
      Item item = new Item();
      item.ownerId = ownerId;
      item.categoryId = input.categoryId().toLowerCase();
      item.name = input.name();
      item.description = input.description();
      item.condition = ItemCondition.valueOf(input.condition());
      item.maxLoanDays = input.maxLoanDays() != null ? input.maxLoanDays() : settings.get().maxLoanDaysDefault();
      item.pickupLocation = input.pickupLocation();
      item.depositNote = input.depositNote();
      items.saveAndFlush(item);
      for (int i = 0; i < urls.size(); i++) {
        ItemImage img = new ItemImage();
        img.itemId = item.id;
        img.url = urls.get(i);
        img.position = i;
        images.save(img);
      }
      images.flush();
      return present(item, List.of(), clock.today());
    } catch (RuntimeException e) {
      urls.forEach(imageService::remove);
      throw e;
    }
  }

  @Transactional
  public ItemDetail update(String id, AuthUser user, UpdateItem cmd, List<MultipartFile> files) {
    Item item = loadOwnedItem(id, user, false);
    if (item.isRemoved) throw forbidden("This item was removed by an administrator");
    UpdateItemRequest in = cmd.fields();
    if (in.categoryId() != null) assertActiveCategory(in.categoryId());

    List<ItemImage> existing = images.findByItemIdOrderByPositionAsc(id);
    Set<String> removeIds = new HashSet<>(cmd.removeImageIds() == null ? List.of() : cmd.removeImageIds());
    List<ItemImage> toRemove = existing.stream().filter(img -> removeIds.contains(img.id)).toList();
    List<ItemImage> kept = existing.stream().filter(img -> !removeIds.contains(img.id)).toList();
    if (kept.size() + files.size() > ImageService.MAX_ITEM_IMAGES) {
      throw unprocessable(ErrorCode.TOO_MANY_IMAGES, "An item can have at most " + ImageService.MAX_ITEM_IMAGES + " images");
    }
    List<String> newUrls = new ArrayList<>();
    try {
      for (MultipartFile f : files) newUrls.add(imageService.store(f));
      images.deleteAll(toRemove);
      // Re-number positions: kept images first, new uploads after.
      for (int i = 0; i < kept.size(); i++) {
        if (kept.get(i).position != i) {
          kept.get(i).position = i;
          images.save(kept.get(i));
        }
      }
      for (int i = 0; i < newUrls.size(); i++) {
        ItemImage img = new ItemImage();
        img.itemId = id;
        img.url = newUrls.get(i);
        img.position = kept.size() + i;
        images.save(img);
      }
      if (in.name() != null) item.name = in.name();
      if (in.categoryId() != null) item.categoryId = in.categoryId().toLowerCase();
      if (in.description() != null) item.description = in.description();
      if (in.condition() != null) item.condition = ItemCondition.valueOf(in.condition());
      if (in.maxLoanDays() != null) item.maxLoanDays = in.maxLoanDays();
      if (in.pickupLocation() != null) item.pickupLocation = in.pickupLocation();
      if (in.depositNote() != null) item.depositNote = in.depositNote();
      else if (cmd.clearDepositNote()) item.depositNote = null;
      items.saveAndFlush(item);
      images.flush();
    } catch (RuntimeException e) {
      newUrls.forEach(imageService::remove);
      throw e;
    }
    List<String> removedUrls = toRemove.stream().map(img -> img.url).toList();
    AfterCommit.run(() -> removedUrls.forEach(imageService::remove));
    return getById(id, user);
  }

  /** FR-7: items with active loans cannot be deleted; open requests are rejected. */
  @Transactional
  public void remove(String id, AuthUser user) {
    Item item = loadOwnedItem(id, user, true);
    long active = loans.countByItemIdAndStatusIn(id, LoanStatus.BLOCKING);
    if (active > 0) throw conflict(ErrorCode.HAS_ACTIVE_LOANS, "Items with approved or active loans cannot be deleted");
    loanService.rejectPendingForItem(id, "The owner deleted this item");
    item = items.findById(id).orElseThrow();
    item.deletedAt = java.time.Instant.now();
    item.isPaused = true;
    items.save(item);
  }

  @Transactional
  public ItemDetail togglePause(String id, AuthUser user) {
    Item item = loadOwnedItem(id, user, false);
    if (item.isRemoved) throw forbidden("This item was removed by an administrator");
    item.isPaused = !item.isPaused;
    items.saveAndFlush(item);
    return getById(id, user);
  }

  // ---------------------------------------------------------------- helpers

  private Item loadOwnedItem(String id, AuthUser user, boolean lock) {
    Item item = (lock ? items.lockById(id) : items.findById(id)).orElse(null);
    if (item == null || item.deletedAt != null) throw notFound("Item");
    if (!item.ownerId.equals(user.id())) throw forbidden("Only the owner can change this item");
    return item;
  }

  private void assertActiveCategory(String categoryId) {
    Category category = categories.findById(categoryId.toLowerCase()).orElse(null);
    if (category == null || !category.isActive) throw unprocessable(ErrorCode.VALIDATION_ERROR, "Unknown or inactive category");
  }

  private Map<String, User> usersById(Set<String> ids) {
    if (ids.isEmpty()) return Map.of();
    return users.findByIdIn(ids).stream().collect(Collectors.toMap(u -> u.id, Function.identity()));
  }

  ItemView present(Item item, List<Loan> blocking, LocalDate today) {
    return presenter(List.of(item)).present(item, blocking, today);
  }

  /** Batch loader for category, images and owner of several items. */
  private Presenter presenter(List<Item> rows) {
    Set<String> ids = rows.stream().map(i -> i.id).collect(Collectors.toSet());
    Map<String, List<ImageView>> imgs = ids.isEmpty() ? Map.of() : images.findByItemIdInOrderByPositionAsc(ids).stream()
        .collect(Collectors.groupingBy(i -> i.itemId, Collectors.mapping(i -> new ImageView(i.id, i.url, i.position), Collectors.toList())));
    Map<String, Category> cats = categories.findByIdIn(rows.stream().map(i -> i.categoryId).collect(Collectors.toSet())).stream()
        .collect(Collectors.toMap(c -> c.id, Function.identity()));
    Map<String, User> owners = usersById(rows.stream().map(i -> i.ownerId).collect(Collectors.toSet()));
    return new Presenter(imgs, cats, owners);
  }

  private record Presenter(Map<String, List<ImageView>> images, Map<String, Category> categories, Map<String, User> owners) {
    ItemView present(Item item, List<Loan> blocking, LocalDate today) {
      Category c = categories.get(item.categoryId);
      return new ItemView(
          item.id, item.name, item.description, item.condition, item.maxLoanDays, item.pickupLocation, item.depositNote,
          item.isPaused, item.isRemoved, item.ratingAvg.doubleValue(), item.ratingCount, ItemViews.deriveStatus(item, blocking, today),
          new CategoryRef(c.id, c.name, c.icon), images.getOrDefault(item.id, List.of()), UserViews.toPublic(owners.get(item.ownerId)),
          item.createdAt, item.updatedAt);
    }
  }
}
