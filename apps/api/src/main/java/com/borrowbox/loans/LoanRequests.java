package com.borrowbox.loans;

import static com.borrowbox.common.web.Sanitizer.clean;
import static com.borrowbox.common.web.Sanitizer.cleanOptional;

import com.borrowbox.common.web.IsoDate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request bodies for loan endpoints; user text is sanitised before validation (TS safeText). */
public final class LoanRequests {
  private LoanRequests() {}

  public record CreateLoanRequest(
      @NotNull(message = "Required") @IsoDate String startDate,
      @NotNull(message = "Required") @IsoDate String endDate,
      @Size(max = 1000) String message) {
    public CreateLoanRequest {
      message = cleanOptional(message);
    }
  }

  public record RejectRequest(@NotNull(message = "Required") @Size(min = 1, max = 500) String reason) {
    public RejectRequest {
      reason = clean(reason);
    }
  }

  public record CancelRequest(@Size(max = 500) String reason) {
    public CancelRequest {
      reason = cleanOptional(reason);
    }
  }

  public record ReturnRequest(@Size(max = 1000) String conditionNote) {
    public ReturnRequest {
      conditionNote = cleanOptional(conditionNote);
    }
  }

  public record ExtensionRequest(@NotNull(message = "Required") @IsoDate String until, @Size(max = 500) String message) {
    public ExtensionRequest {
      message = cleanOptional(message);
    }
  }

  public record ExtensionDecisionRequest(@NotNull(message = "Required") Boolean approve) {}

  public record ReviewRequest(@NotNull(message = "Required") @Min(1) @Max(5) Integer rating, @Size(max = 1000) String comment) {
    public ReviewRequest {
      comment = cleanOptional(comment);
    }
  }
}
