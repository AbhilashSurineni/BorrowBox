package com.borrowbox.common.settings;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Admin-configurable settings (JSON keys are snake_case, as in the TS API). */
public record Settings(
    @JsonProperty("max_loan_days_default") int maxLoanDaysDefault,
    @JsonProperty("request_expiry_hours") int requestExpiryHours,
    @JsonProperty("max_active_loans") int maxActiveLoans,
    @JsonProperty("max_pending_requests") int maxPendingRequests,
    @JsonProperty("reminder_hour") int reminderHour,
    @JsonProperty("auto_complete_days") int autoCompleteDays,
    @JsonProperty("auto_suspend_report_threshold") int autoSuspendReportThreshold) {

  public static final Settings DEFAULTS = new Settings(14, 48, 3, 5, 8, 7, 3);

  /** key -> [min, max], the validation ranges of the TS settingsSchema. */
  public static final java.util.Map<String, int[]> RANGES = java.util.Map.of(
      "max_loan_days_default", new int[] {1, 365},
      "request_expiry_hours", new int[] {1, 24 * 30},
      "max_active_loans", new int[] {1, 50},
      "max_pending_requests", new int[] {1, 50},
      "reminder_hour", new int[] {0, 23},
      "auto_complete_days", new int[] {1, 90},
      "auto_suspend_report_threshold", new int[] {1, 50});
}
