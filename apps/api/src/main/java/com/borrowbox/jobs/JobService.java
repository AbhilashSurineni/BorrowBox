package com.borrowbox.jobs;

import com.borrowbox.common.settings.SettingsService;
import com.borrowbox.common.time.CampusClock;
import com.borrowbox.items.Item;
import com.borrowbox.items.ItemRepository;
import com.borrowbox.loans.Loan;
import com.borrowbox.loans.LoanRepository;
import com.borrowbox.loans.LoanService;
import com.borrowbox.loans.LoanStatus;
import com.borrowbox.notifications.NotificationService;
import com.borrowbox.notifications.NotificationService.Notify;
import com.borrowbox.notifications.NotificationType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Section 11 jobs. Each is idempotent and safe to run concurrently on several instances; every
 * status change goes through LoanService. Scheduled notifications carry dedupe keys.
 */
@Service
public class JobService {
  private final JobRunner runner;
  private final LoanService loanService;
  private final LoanRepository loans;
  private final ItemRepository items;
  private final NotificationService notifications;
  private final SettingsService settings;
  private final CampusClock clock;

  public JobService(
      JobRunner runner,
      LoanService loanService,
      LoanRepository loans,
      ItemRepository items,
      NotificationService notifications,
      SettingsService settings,
      CampusClock clock) {
    this.runner = runner;
    this.loanService = loanService;
    this.loans = loans;
    this.items = items;
    this.notifications = notifications;
    this.settings = settings;
    this.clock = clock;
  }

  /** Every 15 min: PENDING older than request_expiry_hours -> EXPIRED, borrower notified. */
  public JobResult expirePendingRequests(Instant now) {
    return runner.run("expire-pending", null, () -> Map.of("expired", loanService.expirePending(now)));
  }

  /** Hourly: ACTIVE loans with end_date before today -> OVERDUE. */
  public JobResult markOverdueLoans(Instant now) {
    return runner.run("mark-overdue", null, () -> Map.of("overdue", loanService.markOverdue(now)));
  }

  /** Daily: RETURNED loans older than auto_complete_days -> COMPLETED. */
  public JobResult autoCompleteLoans(Instant now) {
    return runner.run("auto-complete", clock.today(now).toString(), () -> Map.of("completed", loanService.autoComplete(now)));
  }

  private boolean isReminderTime(Instant now) {
    return clock.currentHour(now) >= settings.get().reminderHour();
  }

  /**
   * Daily at reminder_hour (default 08:00): borrowers due tomorrow, due today or overdue; owners
   * of overdue items. Runs once per day; per-notification dedupe keys make retries safe.
   */
  public JobResult sendDueReminders(Instant now) {
    if (!isReminderTime(now)) return JobResult.skipped("due-reminders", "not-due");
    LocalDate today = clock.today(now);
    return runner.run("due-reminders", today.toString(), () -> {
      List<Loan> due = loans.findDueOrOverdue(List.of(today, today.plusDays(1)));
      Map<String, Item> itemMap = itemsById(due);
      int sent = 0;
      for (Loan l : due) {
        Item item = itemMap.get(l.itemId);
        String name = item.name;
        String dueDate = l.endDate.toString();
        String link = "/loans/" + l.id;
        if (l.status == LoanStatus.OVERDUE) {
          if (notifications.notify(Notify.of(l.borrowerId, NotificationType.OVERDUE_REMINDER, "Overdue: please return " + name,
              name + " was due on " + dueDate + ". Please return it as soon as possible. You cannot make new requests until it is returned.",
              link).dedupe("overdue:" + l.id + ":" + today))) sent++;
          if (notifications.notify(Notify.of(item.ownerId, NotificationType.OVERDUE_REMINDER, name + " is overdue",
              "Your " + name + " was due back on " + dueDate + " and has not been returned yet. We have reminded the borrower.",
              link).dedupe("overdue-owner:" + l.id + ":" + today))) sent++;
        } else {
          boolean dueToday = l.endDate.equals(today);
          if (notifications.notify(Notify.of(l.borrowerId, NotificationType.DUE_REMINDER,
              dueToday ? name + " is due today" : name + " is due tomorrow", "Please return " + name + " by " + dueDate + ".",
              link).dedupe((dueToday ? "due-today" : "due-tomorrow") + ":" + l.id + ":" + dueDate))) sent++;
        }
      }
      return Map.of("sent", sent);
    });
  }

  /** Daily: remind both parties of approved loans starting today. */
  public JobResult sendStartDateNudges(Instant now) {
    if (!isReminderTime(now)) return JobResult.skipped("start-nudge", "not-due");
    LocalDate today = clock.today(now);
    return runner.run("start-nudge", today.toString(), () -> {
      List<Loan> starting = loans.findByStatusAndStartDate(LoanStatus.APPROVED, today);
      Map<String, Item> itemMap = itemsById(starting);
      int sent = 0;
      for (Loan l : starting) {
        Item item = itemMap.get(l.itemId);
        String link = "/loans/" + l.id;
        if (notifications.notify(Notify.of(l.borrowerId, NotificationType.LOAN_STARTING, item.name + ": loan starts today",
            "Your loan of " + item.name + " starts today. Pickup location: " + item.pickupLocation + ".", link)
            .dedupe("start:" + l.id + ":borrower"))) sent++;
        if (notifications.notify(Notify.of(item.ownerId, NotificationType.LOAN_STARTING, item.name + ": loan starts today",
            item.name + " is due to be handed over today. Remember to mark it as handed over.", link)
            .dedupe("start:" + l.id + ":owner"))) sent++;
      }
      return Map.of("sent", sent);
    });
  }

  private Map<String, Item> itemsById(Collection<Loan> list) {
    if (list.isEmpty()) return Map.of();
    return items.findByIdIn(list.stream().map(l -> l.itemId).collect(Collectors.toSet())).stream()
        .collect(Collectors.toMap(i -> i.id, Function.identity()));
  }
}
