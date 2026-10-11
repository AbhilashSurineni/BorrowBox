package com.borrowbox.loans;

import com.borrowbox.common.errors.ValidationErrors;
import com.borrowbox.common.security.AuthUser;
import com.borrowbox.common.security.Verified;
import com.borrowbox.common.web.Ids;
import com.borrowbox.common.web.QueryParams;
import com.borrowbox.loans.LoanRequests.*;
import com.borrowbox.loans.LoanService.CreateLoan;
import com.borrowbox.loans.LoanViews.LoanView;
import com.borrowbox.common.web.Paged;
import com.borrowbox.reviews.ReviewService;
import com.borrowbox.reviews.ReviewService.CreatedReview;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** HTTP layer only; every rule and status change lives in LoanService. */
@RestController
public class LoanController {
  private final LoanService loans;
  private final ReviewService reviews;

  public LoanController(LoanService loans, ReviewService reviews) {
    this.loans = loans;
    this.reviews = reviews;
  }

  @PostMapping("/api/v1/items/{id}/loans")
  @ResponseStatus(HttpStatus.CREATED)
  public LoanView create(@Verified AuthUser user, @PathVariable String id, @Valid @RequestBody CreateLoanRequest body) {
    String itemId = Ids.require(id);
    LocalDate start = LocalDate.parse(body.startDate());
    LocalDate end = LocalDate.parse(body.endDate());
    if (end.isBefore(start)) throw ValidationErrors.single("endDate", "endDate must be on or after startDate");
    return loans.create(user, itemId, new CreateLoan(start, end, body.message()));
  }

  @GetMapping("/api/v1/loans")
  public Paged<LoanView> list(AuthUser user, @RequestParam Map<String, String> params) {
    QueryParams q = new QueryParams(params);
    String role = q.string("role", 10);
    if (role != null && !role.equals("borrower") && !role.equals("owner")) q.errors().field("role", "Invalid enum value '" + role + "'");
    var statuses = q.enumList("status", LoanStatus.class);
    var page = q.page();
    q.done();
    return loans.list(user, role, statuses, page);
  }

  @GetMapping("/api/v1/loans/{id}")
  public LoanView get(AuthUser user, @PathVariable String id) {
    return loans.get(user, Ids.require(id));
  }

  @PostMapping("/api/v1/loans/{id}/approve")
  public LoanView approve(@Verified AuthUser user, @PathVariable String id) {
    return loans.approve(user, Ids.require(id));
  }

  @PostMapping("/api/v1/loans/{id}/reject")
  public LoanView reject(AuthUser user, @PathVariable String id, @Valid @RequestBody RejectRequest body) {
    return loans.reject(user, Ids.require(id), body.reason());
  }

  @PostMapping("/api/v1/loans/{id}/cancel")
  public LoanView cancel(AuthUser user, @PathVariable String id, @Valid @RequestBody(required = false) CancelRequest body) {
    return loans.cancel(user, Ids.require(id), body == null ? null : body.reason());
  }

  @PostMapping("/api/v1/loans/{id}/handover")
  public LoanView handover(AuthUser user, @PathVariable String id) {
    return loans.handover(user, Ids.require(id));
  }

  @PostMapping("/api/v1/loans/{id}/return")
  public LoanView markReturned(AuthUser user, @PathVariable String id, @Valid @RequestBody(required = false) ReturnRequest body) {
    return loans.markReturned(user, Ids.require(id), body == null ? null : body.conditionNote());
  }

  @PostMapping("/api/v1/loans/{id}/extension")
  public LoanView requestExtension(AuthUser user, @PathVariable String id, @Valid @RequestBody ExtensionRequest body) {
    return loans.requestExtension(user, Ids.require(id), LocalDate.parse(body.until()), body.message());
  }

  @PostMapping("/api/v1/loans/{id}/extension/decision")
  public LoanView decideExtension(AuthUser user, @PathVariable String id, @Valid @RequestBody ExtensionDecisionRequest body) {
    return loans.decideExtension(user, Ids.require(id), body.approve());
  }

  @PostMapping("/api/v1/loans/{id}/reviews")
  @ResponseStatus(HttpStatus.CREATED)
  public CreatedReview review(AuthUser user, @PathVariable String id, @Valid @RequestBody ReviewRequest body) {
    return reviews.create(user, Ids.require(id), body.rating(), body.comment());
  }
}
