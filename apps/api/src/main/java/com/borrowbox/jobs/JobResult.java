package com.borrowbox.jobs;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobResult(String job, String skipped, Object result) {
  public static JobResult skipped(String job, String reason) {
    return new JobResult(job, reason, null);
  }
}
