package com.borrowbox.reports;

import com.borrowbox.common.security.AuthUser;
import com.borrowbox.common.web.Sanitizer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
  private final ReportService reports;

  public ReportController(ReportService reports) {
    this.reports = reports;
  }

  public record CreateReportRequest(
      @NotNull(message = "Required") @Pattern(regexp = "ITEM|USER", message = "Invalid enum value") String targetType,
      @NotNull(message = "Required")
          @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$", message = "Invalid uuid")
          String targetId,
      @NotNull(message = "Required") @Pattern(regexp = "SPAM|INAPPROPRIATE|FRAUD|DAMAGED_ITEM|NOT_RETURNED|HARASSMENT|OTHER", message = "Invalid enum value") String reason,
      @NotNull(message = "Required") @Size(min = 1, max = 2000) String description) {
    public CreateReportRequest {
      description = Sanitizer.clean(description);
    }
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ReportService.ReportView create(AuthUser user, @Valid @RequestBody CreateReportRequest body) {
    return reports.create(user, ReportTargetType.valueOf(body.targetType()), body.targetId().toLowerCase(), ReportReason.valueOf(body.reason()), body.description());
  }
}
