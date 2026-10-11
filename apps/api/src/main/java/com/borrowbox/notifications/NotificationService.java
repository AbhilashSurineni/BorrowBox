package com.borrowbox.notifications;

import com.borrowbox.common.config.AfterCommit;
import com.borrowbox.common.config.AppProperties;
import com.borrowbox.common.errors.BusinessException;
import com.borrowbox.common.mail.MailService;
import com.borrowbox.common.web.Ids;
import com.borrowbox.common.web.PageQuery;
import com.borrowbox.common.web.Paged;
import com.borrowbox.users.User;
import com.borrowbox.users.UserRepository;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** In-app notifications plus the matching email, sent only after the transaction commits. */
@Service
public class NotificationService {
  private final NotificationRepository repo;
  private final UserRepository users;
  private final MailService mail;
  private final AppProperties props;

  public NotificationService(NotificationRepository repo, UserRepository users, MailService mail, AppProperties props) {
    this.repo = repo;
    this.users = users;
    this.mail = mail;
    this.props = props;
  }

  public record Notify(String userId, NotificationType type, String title, String body, String link, String dedupeKey, boolean email) {
    public static Notify of(String userId, NotificationType type, String title, String body, String link) {
      return new Notify(userId, type, title, body, link, null, true);
    }

    public Notify dedupe(String key) {
      return new Notify(userId, type, title, body, link, key, email);
    }
  }

  public record NotificationView(String id, String type, String title, String body, String link, boolean isRead, Instant createdAt) {}

  /** Returns false when a notification with the same dedupeKey already exists (idempotent jobs). */
  @Transactional
  public boolean notify(Notify n) {
    int inserted = repo.insertIgnoringDuplicate(Ids.newId(), n.userId(), n.type().name(), n.title(), n.body(), n.link(), n.dedupeKey(), Instant.now());
    if (inserted == 0) return false;
    if (n.email()) {
      User user = users.findById(n.userId()).orElse(null);
      if (user != null && !user.isDeleted()) {
        String link = n.link() == null ? "" : "\n\nOpen in BorrowBox: " + props.appUrl() + n.link();
        String to = user.email;
        AfterCommit.run(() -> mail.send(to, "BorrowBox: " + n.title(), n.body() + link));
      }
    }
    return true;
  }

  @Transactional(readOnly = true)
  public Paged<NotificationView> list(String userId, boolean unreadOnly, PageQuery q) {
    Page<Notification> page = unreadOnly
        ? repo.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, q.pageable(org.springframework.data.domain.Sort.unsorted()))
        : repo.findByUserIdOrderByCreatedAtDesc(userId, q.pageable(org.springframework.data.domain.Sort.unsorted()));
    long unread = repo.countByUserIdAndIsReadFalse(userId);
    return Paged.of(page.getContent().stream().map(NotificationService::view).toList(), page.getTotalElements(), q).withUnreadCount(unread);
  }

  @Transactional
  public NotificationView markRead(String userId, String id) {
    if (repo.markRead(id, userId) == 0) throw BusinessException.notFound("Notification");
    return repo.findById(id).map(NotificationService::view).orElseThrow(() -> BusinessException.notFound("Notification"));
  }

  @Transactional
  public int markAllRead(String userId) {
    return repo.markAllRead(userId);
  }

  private static NotificationView view(Notification n) {
    return new NotificationView(n.id, n.type, n.title, n.body, n.link, n.isRead, n.createdAt);
  }
}
