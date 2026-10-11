package com.borrowbox.admin;

import static com.borrowbox.common.errors.BusinessException.conflict;
import static com.borrowbox.common.errors.BusinessException.forbidden;
import static com.borrowbox.common.errors.BusinessException.notFound;

import com.borrowbox.admin.AdminStatsRepository.Ranked;
import com.borrowbox.auth.RefreshTokenRepository;
import com.borrowbox.common.audit.AuditLog;
import com.borrowbox.common.audit.AuditLogRepository;
import com.borrowbox.common.audit.AuditService;
import com.borrowbox.common.config.AfterCommit;
import com.borrowbox.common.errors.ErrorCode;
import com.borrowbox.common.mail.MailService;
import com.borrowbox.common.security.AuthUser;
import com.borrowbox.common.settings.Settings;
import com.borrowbox.common.settings.SettingsService;
import com.borrowbox.common.time.CampusClock;
import com.borrowbox.common.web.Ids;
import com.borrowbox.common.web.PageQuery;
import com.borrowbox.common.web.Paged;
import com.borrowbox.items.Category;
import com.borrowbox.items.CategoryRepository;
import com.borrowbox.items.Item;
import com.borrowbox.items.ItemCondition;
import com.borrowbox.items.ItemImageRepository;
import com.borrowbox.items.ItemRepository;
import com.borrowbox.loans.Loan;
import com.borrowbox.loans.LoanRepository;
import com.borrowbox.loans.LoanService;
import com.borrowbox.loans.LoanStatus;
import com.borrowbox.loans.LoanViews;
import com.borrowbox.loans.LoanViews.LoanView;
import com.borrowbox.notifications.Notification;
import com.borrowbox.notifications.NotificationRepository;
import com.borrowbox.notifications.NotificationService;
import com.borrowbox.notifications.NotificationService.Notify;
import com.borrowbox.notifications.NotificationType;
import com.borrowbox.reports.Report;
import com.borrowbox.reports.ReportReason;
import com.borrowbox.reports.ReportRepository;
import com.borrowbox.reports.ReportStatus;
import com.borrowbox.reports.ReportTargetType;
import com.borrowbox.users.Role;
import com.borrowbox.users.User;
import com.borrowbox.users.UserRepository;
import com.borrowbox.users.UserStatus;
import com.borrowbox.users.UserViews;
import com.borrowbox.users.UserViews.Contact;
import com.borrowbox.users.UserViews.PublicUser;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
  private static final Logger log = LoggerFactory.getLogger(AdminService.class);

  private final UserRepository users;
  private final ItemRepository items;
  private final ItemImageRepository images;
  private final CategoryRepository categories;
  private final LoanRepository loans;
  private final ReportRepository reports;
  private final AuditLogRepository auditLogs;
  private final AnnouncementRepository announcements;
  private final NotificationRepository notificationRepo;
  private final RefreshTokenRepository refreshTokens;
  private final AdminStatsRepository stats;
  private final LoanService loanService;
  private final LoanViews loanViews;
  private final NotificationService notifications;
  private final AuditService audit;
  private final SettingsService settings;
  private final MailService mail;
  private final CampusClock clock;
  private final ObjectMapper mapper;

  public AdminService(
      UserRepository users,
      ItemRepository items,
      ItemImageRepository images,
      CategoryRepository categories,
      LoanRepository loans,
      ReportRepository reports,
      AuditLogRepository auditLogs,
      AnnouncementRepository announcements,
      NotificationRepository notificationRepo,
      RefreshTokenRepository refreshTokens,
      AdminStatsRepository stats,
      LoanService loanService,
      LoanViews loanViews,
      NotificationService notifications,
      AuditService audit,
      SettingsService settings,
      MailService mail,
      CampusClock clock,
      ObjectMapper mapper) {
    this.users = users;
    this.items = items;
    this.images = images;
    this.categories = categories;
    this.loans = loans;
    this.reports = reports;
    this.auditLogs = auditLogs;
    this.announcements = announcements;
    this.notificationRepo = notificationRepo;
    this.refreshTokens = refreshTokens;
    this.stats = stats;
    this.loanService = loanService;
    this.loanViews = loanViews;
    this.notifications = notifications;
    this.audit = audit;
    this.settings = settings;
    this.mail = mail;
    this.clock = clock;
    this.mapper = mapper;
  }

  // ---------------------------------------------------------------- dashboard

  public record Cards(long totalUsers, long activeItems, long activeLoans, long overdueLoans, long pendingRequests, long openReports) {}

  public record WeekPoint(LocalDate week, long requested, long approved) {}

  public record CategoryPoint(String id, String name, long loans) {}

  public record LenderPoint(String id, String name, double ratingAvg, long loans) {}

  public record Charts(List<WeekPoint> loansPerWeek, List<CategoryPoint> topCategories, List<LenderPoint> topLenders) {}

  public record Stats(Cards cards, double overdueRate, Charts charts) {}

  @Transactional(readOnly = true)
  public Stats stats() {
    LocalDate today = clock.today();
    long overdue = loans.countByStatus(LoanStatus.OVERDUE);
    Cards cards = new Cards(
        users.countByDeletedAtIsNull(),
        items.countByIsRemovedFalseAndDeletedAtIsNullAndIsPausedFalse(),
        loans.countByStatus(LoanStatus.ACTIVE),
        overdue,
        loans.countByStatus(LoanStatus.PENDING),
        reports.countByStatusIn(List.of(ReportStatus.OPEN, ReportStatus.UNDER_REVIEW)));
    long totalLoans = loans.countByStatusIn(List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE, LoanStatus.RETURNED, LoanStatus.COMPLETED));
    double overdueRate = totalLoans == 0 ? 0 : BigDecimal.valueOf((double) overdue / totalLoans).setScale(3, RoundingMode.HALF_UP).doubleValue();

    LocalDate thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate firstWeek = thisWeek.minusWeeks(11);
    var counts = stats.loansPerWeek(firstWeek.atStartOfDay().toInstant(ZoneOffset.UTC));
    List<WeekPoint> weeks = new ArrayList<>();
    for (LocalDate w = firstWeek; !w.isAfter(thisWeek); w = w.plusWeeks(1)) {
      var c = counts.get(w);
      weeks.add(new WeekPoint(w, c == null ? 0 : c.requested(), c == null ? 0 : c.approved()));
    }
    return new Stats(cards, overdueRate, new Charts(
        weeks,
        stats.topCategories().stream().map(r -> new CategoryPoint(r.id(), r.name(), r.loans())).toList(),
        stats.topLenders().stream().map((Ranked r) -> new LenderPoint(r.id(), r.name(), r.ratingAvg(), r.loans())).toList()));
  }

  // ---------------------------------------------------------------- users

  public record AdminUser(@JsonUnwrapped UserViews.PrivateUser user, int warningCount, long itemCount, long loanCount, long reportCount) {}

  private static String like(String q) {
    if (q == null) return null;
    return "%" + q.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }

  @Transactional(readOnly = true)
  public Paged<AdminUser> listUsers(String q, UserStatus status, Role role, PageQuery page) {
    Page<User> result = users.adminSearch(like(q), status, role, page.pageable(Sort.unsorted()));
    List<String> ids = result.getContent().stream().map(u -> u.id).toList();
    Map<String, Long> itemCounts = ids.isEmpty() ? Map.of() : toCounts(items.countByOwners(ids));
    Map<String, Long> loanCounts = ids.isEmpty() ? Map.of() : toCounts(loans.countByBorrowers(ids));
    Map<String, Long> reportCounts = ids.isEmpty() ? Map.of()
        : toCounts(reports.countByTargetUsers(ids, List.of(ReportStatus.OPEN, ReportStatus.UNDER_REVIEW, ReportStatus.RESOLVED)));
    return Paged.of(result.getContent().stream()
        .map(u -> new AdminUser(UserViews.toPrivate(u), u.warningCount, itemCounts.getOrDefault(u.id, 0L),
            loanCounts.getOrDefault(u.id, 0L), reportCounts.getOrDefault(u.id, 0L)))
        .toList(), result.getTotalElements(), page);
  }

  public enum UserAction { WARN, SUSPEND, BAN, REINSTATE }

  public record ModeratedUser(@JsonUnwrapped UserViews.PrivateUser user, int warningCount) {}

  @Transactional
  public ModeratedUser setUserStatus(AuthUser admin, String userId, UserAction action, String reason) {
    User u = users.findById(userId).filter(x -> !x.isDeleted()).orElseThrow(() -> notFound("User"));
    if (u.id.equals(admin.id())) throw forbidden("You cannot change your own status");
    if (u.role == Role.ADMIN) throw forbidden("Administrator accounts cannot be moderated here");
    UserStatus from = u.status;
    switch (action) {
      case WARN -> u.warningCount += 1;
      case SUSPEND -> u.status = UserStatus.SUSPENDED;
      case BAN -> u.status = UserStatus.BANNED;
      case REINSTATE -> u.status = UserStatus.ACTIVE;
    }
    users.saveAndFlush(u);
    if (action == UserAction.SUSPEND || action == UserAction.BAN) refreshTokens.revokeAllForUser(userId, Instant.now());
    audit.log(admin.id(), "USER_" + action, "User", userId, ordered("reason", reason, "from", from, "to", u.status));
    String message = switch (action) {
      case WARN -> "You have received a warning from the BorrowBox moderators";
      case SUSPEND -> "Your account has been suspended";
      case BAN -> "Your account has been banned";
      case REINSTATE -> "Your account has been reinstated";
    };
    notifications.notify(Notify.of(userId, NotificationType.ACCOUNT, message, message + ". Reason: " + reason, null));
    return new ModeratedUser(UserViews.toPrivate(u), u.warningCount);
  }

  // ---------------------------------------------------------------- items

  public record AdminCategoryRef(String id, String name) {}

  public record AdminOwner(@JsonUnwrapped PublicUser user, String email, UserStatus status) {}

  public record AdminItem(
      String id,
      String name,
      ItemCondition condition,
      boolean isPaused,
      boolean isRemoved,
      String removedReason,
      double ratingAvg,
      String coverImageUrl,
      AdminCategoryRef category,
      AdminOwner owner,
      long loanCount,
      Instant createdAt) {}

  @Transactional(readOnly = true)
  public Paged<AdminItem> listItems(String q, String category, Boolean removed, String ownerId, PageQuery page) {
    Page<Item> result = items.adminSearch(like(q), category, removed, ownerId, page.pageable(Sort.unsorted()));
    List<Item> rows = result.getContent();
    Set<String> ids = rows.stream().map(i -> i.id).collect(Collectors.toSet());
    Map<String, String> covers = new HashMap<>();
    if (!ids.isEmpty()) for (Object[] r : images.findCovers(ids)) covers.putIfAbsent((String) r[0], (String) r[1]);
    Map<String, Long> loanCounts = ids.isEmpty() ? Map.of() : toCounts(loans.countByItems(ids));
    Map<String, Category> cats = categories.findByIdIn(rows.stream().map(i -> i.categoryId).collect(Collectors.toSet())).stream()
        .collect(Collectors.toMap(c -> c.id, Function.identity()));
    Map<String, User> owners = byId(users.findByIdIn(rows.stream().map(i -> i.ownerId).collect(Collectors.toSet())));
    return Paged.of(rows.stream().map(i -> {
      User o = owners.get(i.ownerId);
      Category c = cats.get(i.categoryId);
      return new AdminItem(i.id, i.name, i.condition, i.isPaused, i.isRemoved, i.removedReason, i.ratingAvg.doubleValue(),
          covers.get(i.id), new AdminCategoryRef(c.id, c.name), new AdminOwner(UserViews.toPublic(o), o.email, o.status),
          loanCounts.getOrDefault(i.id, 0L), i.createdAt);
    }).toList(), result.getTotalElements(), page);
  }

  public record RemovedItem(String id, boolean isRemoved, String removedReason) {}

  /** FR-28: take an item down. Pending requests are rejected and upcoming approved loans cancelled. */
  @Transactional
  public RemovedItem removeItem(AuthUser admin, String itemId, String reason) {
    Item item = items.lockById(itemId).filter(i -> i.deletedAt == null).orElseThrow(() -> notFound("Item"));
    if (item.isRemoved) throw conflict(ErrorCode.CONFLICT, "Item is already removed");
    item.isRemoved = true;
    item.removedReason = reason;
    items.saveAndFlush(item);
    String ownerId = item.ownerId;
    String name = item.name;
    loanService.rejectPendingForItem(itemId, "The item was removed by a moderator");
    loanService.cancelApprovedForItem(itemId, "The item was removed by a moderator", admin.id());
    audit.log(admin.id(), "ITEM_REMOVED", "Item", itemId, Map.of("reason", reason));
    notifications.notify(Notify.of(ownerId, NotificationType.ACCOUNT, "Your item \"" + name + "\" was removed",
        "A moderator removed \"" + name + "\". Reason: " + reason, "/items/" + itemId));
    return new RemovedItem(itemId, true, reason);
  }

  // ---------------------------------------------------------------- loans

  public record AdminLoan(@JsonUnwrapped LoanView loan, Contact borrowerContact, Contact ownerContact, long daysOverdue) {}

  @Transactional(readOnly = true)
  public Paged<AdminLoan> listLoans(AuthUser admin, List<LoanStatus> statuses, String itemId, String userId, PageQuery page) {
    boolean all = statuses == null || statuses.isEmpty();
    Page<Loan> result = loans.findForAdmin(all, all ? List.of(LoanStatus.PENDING) : statuses, itemId, userId, page.pageable(Sort.unsorted()));
    LocalDate today = clock.today();
    return Paged.of(loanViews.contexts(result.getContent()).stream().map(c -> new AdminLoan(
        LoanViews.present(c, admin),
        // Admins need both parties' contact details to follow up on overdue items.
        UserViews.toContact(c.borrower()),
        UserViews.toContact(c.owner()),
        c.loan().status == LoanStatus.OVERDUE ? Math.max(0, ChronoUnit.DAYS.between(c.loan().endDate, today)) : 0)).toList(),
        result.getTotalElements(), page);
  }

  /** FR-31: one-click reminder to the borrower. */
  @Transactional
  public Map<String, Boolean> remind(AuthUser admin, String loanId, String message) {
    Loan loan = loans.findById(loanId).orElseThrow(() -> notFound("Loan"));
    if (!loan.status.isBlocking()) {
      throw conflict(ErrorCode.INVALID_TRANSITION, "Reminders can only be sent for approved, active or overdue loans");
    }
    String name = items.findById(loan.itemId).orElseThrow().name;
    boolean overdue = loan.status == LoanStatus.OVERDUE;
    String due = loan.endDate.toString();
    String body = (overdue
        ? name + " was due on " + due + ". Please return it as soon as possible."
        : name + " is due back on " + due + ".")
        + (message != null ? "\n\nMessage from the moderators: " + message : "");
    notifications.notify(Notify.of(loan.borrowerId, overdue ? NotificationType.OVERDUE_REMINDER : NotificationType.DUE_REMINDER,
        overdue ? "Overdue: please return " + name : "Reminder: " + name, body, "/loans/" + loan.id));
    Map<String, Object> meta = new LinkedHashMap<>();
    if (message != null) meta.put("message", message);
    audit.log(admin.id(), "LOAN_REMINDER_SENT", "Loan", loanId, meta);
    return Map.of("sent", true);
  }

  // ---------------------------------------------------------------- reports

  public record AccountableUser(String id, String name, String email, UserStatus status) {}

  public record ResolvedBy(String id, String name) {}

  public record AdminReport(
      String id,
      ReportTargetType targetType,
      String targetId,
      Object target,
      AccountableUser accountableUser,
      long accountableUserUpheldReports,
      ReportReason reason,
      String description,
      ReportStatus status,
      String adminNote,
      PublicUser reporter,
      ResolvedBy resolvedBy,
      Instant resolvedAt,
      Instant createdAt) {}

  public record ItemTarget(String id, String name, boolean isRemoved) {}

  @Transactional(readOnly = true)
  public Paged<AdminReport> listReports(ReportStatus status, ReportTargetType targetType, PageQuery page) {
    Page<Report> result = reports.findQueue(status, targetType, page.pageable(Sort.unsorted()));
    List<Report> rows = result.getContent();
    Set<String> itemIds = rows.stream().filter(r -> r.targetType == ReportTargetType.ITEM).map(r -> r.targetId).collect(Collectors.toSet());
    Set<String> userIds = rows.stream().map(r -> r.targetUserId).collect(Collectors.toSet());
    Set<String> people = new java.util.HashSet<>(userIds);
    rows.forEach(r -> {
      people.add(r.reporterId);
      if (r.resolvedById != null) people.add(r.resolvedById);
      if (r.targetType == ReportTargetType.USER) people.add(r.targetId);
    });
    Map<String, Item> itemMap = itemIds.isEmpty() ? Map.of() : items.findByIdIn(itemIds).stream().collect(Collectors.toMap(i -> i.id, Function.identity()));
    Map<String, User> userMap = people.isEmpty() ? Map.of() : byId(users.findByIdIn(people));
    Map<String, Long> upheld = userIds.isEmpty() ? Map.of() : toCounts(reports.countByTargetUsers(userIds, List.of(ReportStatus.RESOLVED)));
    return Paged.of(rows.stream().map(r -> {
      Object target;
      if (r.targetType == ReportTargetType.ITEM) {
        Item i = itemMap.get(r.targetId);
        target = i == null ? null : new ItemTarget(i.id, i.name, i.isRemoved);
      } else {
        User u = userMap.get(r.targetId);
        target = u == null ? null : new AccountableUser(u.id, u.name, u.email, u.status);
      }
      User acc = userMap.get(r.targetUserId);
      User resolver = r.resolvedById == null ? null : userMap.get(r.resolvedById);
      return new AdminReport(r.id, r.targetType, r.targetId, target,
          acc == null ? null : new AccountableUser(acc.id, acc.name, acc.email, acc.status),
          upheld.getOrDefault(r.targetUserId, 0L), r.reason, r.description, r.status, r.adminNote,
          UserViews.toPublic(userMap.get(r.reporterId)),
          resolver == null ? null : new ResolvedBy(resolver.id, resolver.name), r.resolvedAt, r.createdAt);
    }).toList(), result.getTotalElements(), page);
  }

  /** Raw report row (as the TS API returned it) plus whether Rule 7 auto-suspended the user. */
  public record UpdatedReport(
      String id,
      String reporterId,
      ReportTargetType targetType,
      String targetId,
      String targetUserId,
      ReportReason reason,
      String description,
      ReportStatus status,
      String adminNote,
      String resolvedById,
      Instant resolvedAt,
      Instant createdAt,
      Instant updatedAt,
      boolean autoSuspended) {}

  @Transactional
  public UpdatedReport updateReport(AuthUser admin, String reportId, ReportStatus status, String adminNote) {
    Report r = reports.findById(reportId).orElseThrow(() -> notFound("Report"));
    if (r.status == ReportStatus.RESOLVED || r.status == ReportStatus.DISMISSED) {
      throw conflict(ErrorCode.CONFLICT, "Report is already " + r.status.name().toLowerCase(Locale.ROOT));
    }
    boolean closing = status == ReportStatus.RESOLVED || status == ReportStatus.DISMISSED;
    r.status = status;
    if (adminNote != null) r.adminNote = adminNote;
    if (closing) {
      r.resolvedById = admin.id();
      r.resolvedAt = Instant.now();
    }
    reports.saveAndFlush(r);
    Map<String, Object> meta = new LinkedHashMap<>();
    if (adminNote != null) meta.put("adminNote", adminNote);
    meta.put("targetType", r.targetType);
    meta.put("targetId", r.targetId);
    audit.log(admin.id(), "REPORT_" + status, "Report", reportId, meta);
    boolean autoSuspended = status == ReportStatus.RESOLVED && applyAutoSuspension(r.targetUserId);
    return new UpdatedReport(r.id, r.reporterId, r.targetType, r.targetId, r.targetUserId, r.reason, r.description, r.status,
        r.adminNote, r.resolvedById, r.resolvedAt, r.createdAt, r.updatedAt, autoSuspended);
  }

  /**
   * Rule 7: three or more upheld (RESOLVED) reports against a user — directly or about their
   * items — suspend the account automatically, pending admin review.
   */
  private boolean applyAutoSuspension(String userId) {
    Settings s = settings.get();
    long upheld = reports.countByTargetUserIdAndStatus(userId, ReportStatus.RESOLVED);
    if (upheld < s.autoSuspendReportThreshold()) return false;
    User u = users.findById(userId).orElse(null);
    if (u == null || u.status != UserStatus.ACTIVE || u.role == Role.ADMIN || u.isDeleted()) return false;
    u.status = UserStatus.SUSPENDED;
    users.saveAndFlush(u);
    refreshTokens.revokeAllForUser(userId, Instant.now());
    audit.log(null, "USER_AUTO_SUSPENDED", "User", userId, ordered("upheldReports", upheld, "threshold", s.autoSuspendReportThreshold()));
    notifications.notify(Notify.of(userId, NotificationType.ACCOUNT, "Your account has been suspended",
        "Your account was suspended after " + upheld + " upheld reports, pending review by an administrator.", null));
    return true;
  }

  // ---------------------------------------------------------------- settings

  @Transactional(readOnly = true)
  public Settings getSettings() {
    return settings.get();
  }

  @Transactional
  public Settings updateSettings(AuthUser admin, Map<String, Object> patch) {
    Settings before = settings.get();
    Settings after = settings.update(patch);
    audit.log(admin.id(), "SETTINGS_UPDATED", "Setting", null, Map.of("before", before, "changes", patch));
    return after;
  }

  // ---------------------------------------------------------------- announcements

  public record AnnouncementView(
      String id, String authorId, String title, String body, String link, String audience, boolean sendEmail, int recipientCount, Instant createdAt) {}

  public record AnnouncementAuthor(String id, String name) {}

  public record AnnouncementListItem(@JsonUnwrapped AnnouncementView announcement, AnnouncementAuthor author) {}

  @Transactional
  public AnnouncementView createAnnouncement(AuthUser admin, String title, String body, String link, String audience, boolean sendEmail) {
    Role role = switch (audience) {
      case "MEMBERS" -> Role.MEMBER;
      case "ADMINS" -> Role.ADMIN;
      default -> null;
    };
    List<User> recipients = users.findAnnouncementRecipients(role);
    Announcement a = new Announcement();
    a.authorId = admin.id();
    a.title = title;
    a.body = body;
    a.link = link;
    a.audience = audience;
    a.sendEmail = sendEmail;
    a.recipientCount = recipients.size();
    announcements.saveAndFlush(a);
    Instant now = Instant.now();
    List<Notification> rows = recipients.stream().map(u -> {
      Notification n = new Notification();
      n.id = Ids.newId();
      n.userId = u.id;
      n.type = NotificationType.ANNOUNCEMENT.name();
      n.title = title;
      n.body = body;
      n.link = link;
      n.createdAt = now;
      return n;
    }).toList();
    notificationRepo.saveAll(rows);
    audit.log(admin.id(), "ANNOUNCEMENT_SENT", "Announcement", a.id,
        ordered("audience", audience, "recipients", recipients.size(), "sendEmail", sendEmail));
    if (sendEmail) {
      List<String> emails = recipients.stream().map(u -> u.email).toList();
      // Sent after commit, sequentially, so a large audience does not flood the SMTP server.
      AfterCommit.run(() -> Thread.startVirtualThread(() -> emails.forEach(to -> {
        try {
          mail.send(to, "BorrowBox: " + title, body);
        } catch (RuntimeException e) {
          log.error("[announcement] email failed", e);
        }
      })));
    }
    return view(a);
  }

  @Transactional(readOnly = true)
  public Paged<AnnouncementListItem> listAnnouncements(PageQuery page) {
    Page<Announcement> result = announcements.findAllByOrderByCreatedAtDesc(page.pageable(Sort.unsorted()));
    Map<String, User> authors = byId(users.findByIdIn(result.getContent().stream().map(a -> a.authorId).collect(Collectors.toSet())));
    return Paged.of(result.getContent().stream()
        .map(a -> new AnnouncementListItem(view(a), new AnnouncementAuthor(a.authorId, authors.get(a.authorId).name)))
        .toList(), result.getTotalElements(), page);
  }

  private static AnnouncementView view(Announcement a) {
    return new AnnouncementView(a.id, a.authorId, a.title, a.body, a.link, a.audience, a.sendEmail, a.recipientCount, a.createdAt);
  }

  // ---------------------------------------------------------------- audit log

  public record AuditActor(String id, String name) {}

  public record AuditView(String id, String actorId, String action, String entity, String entityId, JsonNode metadata, Instant createdAt, AuditActor actor) {}

  @Transactional(readOnly = true)
  public Paged<AuditView> listAuditLogs(String entity, PageQuery page) {
    Page<AuditLog> result = auditLogs.search(entity, page.pageable(Sort.unsorted()));
    Set<String> actorIds = result.getContent().stream().map(a -> a.actorId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
    Map<String, User> actors = actorIds.isEmpty() ? Map.of() : byId(users.findByIdIn(actorIds));
    return Paged.of(result.getContent().stream().map(a -> {
      User actor = a.actorId == null ? null : actors.get(a.actorId);
      return new AuditView(a.id, a.actorId, a.action, a.entity, a.entityId, parse(a.metadata), a.createdAt,
          actor == null ? null : new AuditActor(actor.id, actor.name));
    }).toList(), result.getTotalElements(), page);
  }

  // ---------------------------------------------------------------- helpers

  private JsonNode parse(String json) {
    try {
      return json == null ? null : mapper.readTree(json);
    } catch (Exception e) {
      return null;
    }
  }

  private static Map<String, Long> toCounts(List<Object[]> rows) {
    Map<String, Long> out = new HashMap<>();
    for (Object[] r : rows) out.put((String) r[0], ((Number) r[1]).longValue());
    return out;
  }

  private static Map<String, User> byId(List<User> list) {
    return list.stream().collect(Collectors.toMap(u -> u.id, Function.identity()));
  }

  private static Map<String, Object> ordered(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }
}
