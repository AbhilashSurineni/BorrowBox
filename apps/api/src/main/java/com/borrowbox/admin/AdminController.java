package com.borrowbox.admin;

import static com.borrowbox.common.web.Sanitizer.clean;
import static com.borrowbox.common.web.Sanitizer.cleanOptional;

import com.borrowbox.admin.AdminService.*;
import com.borrowbox.common.security.AuthUser;
import com.borrowbox.common.settings.Settings;
import com.borrowbox.common.web.Ids;
import com.borrowbox.common.web.Paged;
import com.borrowbox.common.web.QueryParams;
import com.borrowbox.items.CategoryService;
import com.borrowbox.loans.LoanStatus;
import com.borrowbox.reports.ReportStatus;
import com.borrowbox.reports.ReportTargetType;
import com.borrowbox.users.Role;
import com.borrowbox.users.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** ADMIN-only endpoints. Every write is recorded in audit_logs by the services. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
  private final AdminService admin;
  private final CategoryService categories;

  public AdminController(AdminService admin, CategoryService categories) {
    this.admin = admin;
    this.categories = categories;
  }

  // ---- request bodies (user text sanitised before validation)

  public record UserStatusRequest(@NotNull(message = "Required") @Pattern(regexp = "WARN|SUSPEND|BAN|REINSTATE", message = "Invalid enum value") String action, @NotNull(message = "Required") @Size(min = 1, max = 500) String reason) {
    public UserStatusRequest {
      reason = clean(reason);
    }
  }

  public record ReasonRequest(@NotNull(message = "Required") @Size(min = 1, max = 500) String reason) {
    public ReasonRequest {
      reason = clean(reason);
    }
  }

  public record RemindRequest(@Size(max = 500) String message) {
    public RemindRequest {
      message = cleanOptional(message);
    }
  }

  public record ReportDecisionRequest(
      @NotNull(message = "Required") @Pattern(regexp = "UNDER_REVIEW|RESOLVED|DISMISSED", message = "Invalid enum value") String status,
      @Size(max = 2000) String adminNote) {
    public ReportDecisionRequest {
      adminNote = cleanOptional(adminNote);
    }
  }

  public record CategoryRequest(@Size(min = 2, max = 60) String name, @Size(max = 60) String icon, Boolean isActive) {
    public CategoryRequest {
      name = clean(name);
      icon = icon == null || icon.isBlank() ? null : icon.trim();
    }
  }

  public record AnnouncementRequest(
      @NotNull(message = "Required") @Size(min = 1, max = 150) String title,
      @NotNull(message = "Required") @Size(min = 1, max = 5000) String body,
      @Size(max = 500) String link,
      @Pattern(regexp = "ALL|MEMBERS|ADMINS", message = "Invalid enum value") String audience,
      Boolean sendEmail) {
    public AnnouncementRequest {
      title = clean(title);
      body = clean(body);
      link = link == null || link.isBlank() ? null : link.trim();
    }
  }

  // ---- dashboard

  @GetMapping("/stats")
  public Stats stats(AuthUser user) {
    return admin.stats();
  }

  // ---- users

  @GetMapping("/users")
  public Paged<AdminUser> users(AuthUser user, @RequestParam Map<String, String> params) {
    QueryParams q = new QueryParams(params);
    var page = q.page();
    String search = q.string("q", 100);
    UserStatus status = q.enumValue("status", UserStatus.class);
    Role role = q.enumValue("role", Role.class);
    q.done();
    return admin.listUsers(search, status, role, page);
  }

  @PatchMapping("/users/{id}/status")
  public ModeratedUser setUserStatus(AuthUser user, @PathVariable String id, @Valid @RequestBody UserStatusRequest body) {
    return admin.setUserStatus(user, Ids.require(id), UserAction.valueOf(body.action()), body.reason());
  }

  // ---- items

  @GetMapping("/items")
  public Paged<AdminItem> items(AuthUser user, @RequestParam Map<String, String> params) {
    QueryParams q = new QueryParams(params);
    var page = q.page();
    String search = q.string("q", 100);
    String category = q.uuid("category");
    Boolean removed = q.bool("removed");
    String ownerId = q.uuid("ownerId");
    q.done();
    return admin.listItems(search, category, removed, ownerId, page);
  }

  @PatchMapping("/items/{id}/remove")
  public RemovedItem removeItem(AuthUser user, @PathVariable String id, @Valid @RequestBody ReasonRequest body) {
    return admin.removeItem(user, Ids.require(id), body.reason());
  }

  // ---- loans

  @GetMapping("/loans")
  public Paged<AdminLoan> loans(AuthUser user, @RequestParam Map<String, String> params) {
    QueryParams q = new QueryParams(params);
    var page = q.page();
    List<LoanStatus> statuses = q.enumList("status", LoanStatus.class);
    String itemId = q.uuid("itemId");
    String userId = q.uuid("userId");
    q.done();
    return admin.listLoans(user, statuses, itemId, userId, page);
  }

  @PostMapping("/loans/{id}/remind")
  public Map<String, Boolean> remind(AuthUser user, @PathVariable String id, @Valid @RequestBody(required = false) RemindRequest body) {
    return admin.remind(user, Ids.require(id), body == null ? null : body.message());
  }

  // ---- reports

  @GetMapping("/reports")
  public Paged<AdminReport> reports(AuthUser user, @RequestParam Map<String, String> params) {
    QueryParams q = new QueryParams(params);
    var page = q.page();
    ReportStatus status = q.enumValue("status", ReportStatus.class);
    ReportTargetType targetType = q.enumValue("targetType", ReportTargetType.class);
    q.done();
    return admin.listReports(status, targetType, page);
  }

  @PatchMapping("/reports/{id}")
  public UpdatedReport updateReport(AuthUser user, @PathVariable String id, @Valid @RequestBody ReportDecisionRequest body) {
    return admin.updateReport(user, Ids.require(id), ReportStatus.valueOf(body.status()), body.adminNote());
  }

  // ---- categories

  @GetMapping("/categories")
  public List<CategoryService.AdminCategory> categories(AuthUser user) {
    return categories.listAll();
  }

  @PostMapping("/categories")
  @ResponseStatus(HttpStatus.CREATED)
  public CategoryService.CategoryView createCategory(AuthUser user, @Valid @RequestBody CategoryRequest body) {
    if (body.name() == null) throw com.borrowbox.common.errors.ValidationErrors.single("name", "Required");
    return categories.create(user.id(), body.name(), body.icon(), body.isActive());
  }

  @PatchMapping("/categories/{id}")
  public CategoryService.CategoryView updateCategory(AuthUser user, @PathVariable String id, @Valid @RequestBody CategoryRequest body) {
    return categories.update(user.id(), Ids.require(id), body.name(), body.icon(), body.isActive());
  }

  @DeleteMapping("/categories/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteCategory(AuthUser user, @PathVariable String id) {
    categories.delete(user.id(), Ids.require(id));
  }

  // ---- settings

  @GetMapping("/settings")
  public Settings settings(AuthUser user) {
    return admin.getSettings();
  }

  @PatchMapping("/settings")
  public Settings updateSettings(AuthUser user, @RequestBody Map<String, Object> body) {
    return admin.updateSettings(user, body);
  }

  // ---- announcements

  @GetMapping("/announcements")
  public Paged<AnnouncementListItem> announcements(AuthUser user, @RequestParam Map<String, String> params) {
    QueryParams q = new QueryParams(params);
    var page = q.page();
    q.done();
    return admin.listAnnouncements(page);
  }

  @PostMapping("/announcements")
  @ResponseStatus(HttpStatus.CREATED)
  public AnnouncementView createAnnouncement(AuthUser user, @Valid @RequestBody AnnouncementRequest body) {
    return admin.createAnnouncement(user, body.title(), body.body(), body.link(),
        body.audience() == null ? "ALL" : body.audience(), Boolean.TRUE.equals(body.sendEmail()));
  }

  // ---- audit log (read-only)

  @GetMapping("/audit-logs")
  public Paged<AuditView> auditLogs(AuthUser user, @RequestParam Map<String, String> params) {
    QueryParams q = new QueryParams(params);
    var page = q.page();
    String entity = q.string("entity", 40);
    q.done();
    return admin.listAuditLogs(entity, page);
  }
}
