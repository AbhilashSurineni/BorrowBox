package com.borrowbox.notifications;

import com.borrowbox.common.security.AuthUser;
import com.borrowbox.common.web.Ids;
import com.borrowbox.common.web.Paged;
import com.borrowbox.common.web.QueryParams;
import com.borrowbox.notifications.NotificationService.NotificationView;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
  private final NotificationService notifications;

  public NotificationController(NotificationService notifications) {
    this.notifications = notifications;
  }

  @GetMapping
  public Paged<NotificationView> list(AuthUser user, @RequestParam Map<String, String> params) {
    QueryParams q = new QueryParams(params);
    var page = q.page();
    Boolean unread = q.bool("unread");
    q.done();
    return notifications.list(user.id(), Boolean.TRUE.equals(unread), page);
  }

  @PatchMapping("/{id}/read")
  public NotificationView markRead(AuthUser user, @PathVariable String id) {
    return notifications.markRead(user.id(), Ids.require(id));
  }

  @PostMapping("/read-all")
  public Map<String, Integer> readAll(AuthUser user) {
    return Map.of("updated", notifications.markAllRead(user.id()));
  }
}
