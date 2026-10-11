package com.borrowbox.jobs;

import java.time.Instant;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Section 11 schedules, in the campus timezone. Disabled with CRON_ENABLED=false (and in tests). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.cron-enabled", havingValue = "true", matchIfMissing = true)
public class JobScheduler {
  private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);
  private final JobService jobs;

  public JobScheduler(JobService jobs) {
    this.jobs = jobs;
  }

  private void run(String name, Function<Instant, JobResult> job) {
    try {
      JobResult res = job.apply(Instant.now());
      if (res.skipped() == null) log.info("[jobs] {} {}", name, res.result());
    } catch (RuntimeException e) {
      log.error("[jobs] {} failed", name, e);
    }
  }

  @Scheduled(cron = "0 */15 * * * *", zone = "${app.timezone}")
  public void expirePending() {
    run("expire-pending", jobs::expirePendingRequests);
  }

  @Scheduled(cron = "0 0 * * * *", zone = "${app.timezone}")
  public void markOverdue() {
    run("mark-overdue", jobs::markOverdueLoans);
  }

  /** Hourly tick; acts once per day at or after the configurable reminder_hour (default 08:00). */
  @Scheduled(cron = "0 5 * * * *", zone = "${app.timezone}")
  public void dueReminders() {
    run("due-reminders", jobs::sendDueReminders);
  }

  @Scheduled(cron = "0 10 * * * *", zone = "${app.timezone}")
  public void startNudge() {
    run("start-nudge", jobs::sendStartDateNudges);
  }

  @Scheduled(cron = "0 20 3 * * *", zone = "${app.timezone}")
  public void autoComplete() {
    run("auto-complete", jobs::autoCompleteLoans);
  }
}
