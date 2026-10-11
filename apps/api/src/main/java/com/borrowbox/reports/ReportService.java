package com.borrowbox.reports;

import com.borrowbox.common.errors.BusinessException;
import com.borrowbox.common.errors.ErrorCode;
import com.borrowbox.common.security.AuthUser;
import com.borrowbox.items.Item;
import com.borrowbox.items.ItemRepository;
import com.borrowbox.users.User;
import com.borrowbox.users.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
  private final ReportRepository reports;
  private final UserRepository users;
  private final ItemRepository items;

  public ReportService(ReportRepository reports, UserRepository users, ItemRepository items) {
    this.reports = reports;
    this.users = users;
    this.items = items;
  }

  public record ReportView(
      String id, ReportTargetType targetType, String targetId, ReportReason reason, String description, ReportStatus status, Instant createdAt) {}

  /** FR-27: report an item or a user. */
  @Transactional
  public ReportView create(AuthUser user, ReportTargetType targetType, String targetId, ReportReason reason, String description) {
    String targetUserId;
    if (targetType == ReportTargetType.USER) {
      User target = users.findById(targetId).filter(u -> !u.isDeleted()).orElseThrow(() -> BusinessException.notFound("User"));
      targetUserId = target.id;
    } else {
      Item item = items.findById(targetId).filter(i -> i.deletedAt == null).orElseThrow(() -> BusinessException.notFound("Item"));
      targetUserId = item.ownerId;
    }
    if (targetUserId.equals(user.id())) {
      throw BusinessException.unprocessable(ErrorCode.CANNOT_REPORT_SELF, "You cannot report yourself or your own items");
    }
    long open = reports.countByReporterIdAndTargetTypeAndTargetIdAndStatusIn(user.id(), targetType, targetId,
        List.of(ReportStatus.OPEN, ReportStatus.UNDER_REVIEW));
    if (open > 0) throw BusinessException.conflict(ErrorCode.DUPLICATE_REPORT, "You already have an open report about this");

    Report r = new Report();
    r.reporterId = user.id();
    r.targetType = targetType;
    r.targetId = targetId;
    r.targetUserId = targetUserId;
    r.reason = reason;
    r.description = description;
    reports.saveAndFlush(r);
    return new ReportView(r.id, r.targetType, r.targetId, r.reason, r.description, r.status, r.createdAt);
  }
}
