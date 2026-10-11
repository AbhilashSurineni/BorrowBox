package com.borrowbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.borrowbox.jobs.JobResult;
import com.borrowbox.jobs.JobService;
import com.borrowbox.loans.LoanStatus;
import com.borrowbox.users.Role;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** The 8 key test cases from Section 15 of the project documentation. */
class KeyFlowsTest extends IntegrationTestBase {
  @Autowired JobService jobs;

  private MvcResult request(TestUser borrower, String itemId, int start, int end) throws Exception {
    return perform(post("/api/v1/items/{id}/loans", itemId), borrower, Map.of("startDate", day(start), "endDate", day(end)));
  }

  @Test
  @DisplayName("1. a request overlapping an approved loan is rejected")
  void overlapRejected() throws Exception {
    TestUser owner = createUser();
    TestUser first = createUser();
    TestUser second = createUser();
    String itemId = createItem(owner.id(), "Calculator").id;
    insertLoan(itemId, first.id(), LoanStatus.APPROVED, 2, 5);

    MvcResult res = request(second, itemId, 4, 6);
    assertThat(res.getResponse().getStatus()).isEqualTo(409);
    assertThat(json(res).at("/error/code").asText()).isEqualTo("DATES_UNAVAILABLE");

    // Adjacent dates are fine; same response shape as the TS API.
    MvcResult ok = request(second, itemId, 6, 8);
    assertThat(ok.getResponse().getStatus()).isEqualTo(201);
    assertThat(json(ok).get("status").asText()).isEqualTo("PENDING");
    assertThat(json(ok).get("itemId").asText()).isEqualTo(itemId);
    assertThat(json(ok).get("startDate").asText()).isEqualTo(day(6));
    assertThat(json(ok).get("endDate").asText()).isEqualTo(day(8));
  }

  @Test
  @DisplayName("2. an owner cannot request their own item")
  void ownItem() throws Exception {
    TestUser owner = createUser();
    String itemId = createItem(owner.id(), "Calculator").id;
    MvcResult res = request(owner, itemId, 1, 2);
    assertThat(res.getResponse().getStatus()).isEqualTo(403);
    assertThat(json(res).at("/error/code").asText()).isEqualTo("OWN_ITEM");
  }

  @Test
  @DisplayName("3. a non-owner cannot approve or reject")
  void nonOwnerCannotDecide() throws Exception {
    TestUser owner = createUser();
    TestUser borrower = createUser();
    TestUser stranger = createUser();
    String itemId = createItem(owner.id(), "Calculator").id;
    String loanId = json(request(borrower, itemId, 1, 3)).get("id").asText();

    for (TestUser user : List.of(borrower, stranger)) {
      assertThat(perform(post("/api/v1/loans/{id}/approve", loanId), user, null).getResponse().getStatus()).isEqualTo(403);
      assertThat(perform(post("/api/v1/loans/{id}/reject", loanId), user, Map.of("reason", "nope")).getResponse().getStatus()).isEqualTo(403);
    }
    assertThat(loanStatus(loanId)).isEqualTo("PENDING");

    // Contact details stay hidden until approval, then appear for the counterparty only.
    assertThat(json(perform(get("/api/v1/loans/{id}", loanId), borrower, null)).get("counterpartyContact").isNull()).isTrue();
    MvcResult approved = perform(post("/api/v1/loans/{id}/approve", loanId), owner, null);
    assertThat(approved.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(approved).get("status").asText()).isEqualTo("APPROVED");
    assertThat(json(perform(get("/api/v1/loans/{id}", loanId), borrower, null)).at("/counterpartyContact/email").asText()).isEqualTo(owner.email());
    assertThat(perform(get("/api/v1/loans/{id}", loanId), stranger, null).getResponse().getStatus()).isEqualTo(404);
  }

  @Test
  @DisplayName("4. an overdue borrower cannot create new requests")
  void overdueBorrowerBlocked() throws Exception {
    TestUser owner = createUser();
    TestUser borrower = createUser();
    String itemId = createItem(owner.id(), "Calculator").id;
    String otherId = createItem(owner.id(), "Another item").id;
    String loanId = insertLoan(itemId, borrower.id(), LoanStatus.ACTIVE, -5, -1);

    JobResult job = jobs.markOverdueLoans(Instant.now());
    assertThat(job.result()).isEqualTo(Map.of("overdue", 1));
    assertThat(loanStatus(loanId)).isEqualTo("OVERDUE");

    MvcResult res = request(borrower, otherId, 1, 2);
    assertThat(res.getResponse().getStatus()).isEqualTo(403);
    assertThat(json(res).at("/error/code").asText()).isEqualTo("OVERDUE_LOAN_EXISTS");
  }

  @Test
  @DisplayName("5. a pending request expires after 48 hours")
  void pendingExpires() throws Exception {
    TestUser owner = createUser();
    TestUser borrower = createUser();
    String itemId = createItem(owner.id(), "Calculator").id;
    String otherId = createItem(owner.id(), "Fresh item").id;
    String old = json(request(borrower, itemId, 5, 6)).get("id").asText();
    String fresh = json(request(borrower, otherId, 5, 6)).get("id").asText();
    jdbc.update("UPDATE loans SET created_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now().minus(49, ChronoUnit.HOURS)), old);
    jdbc.update("UPDATE loans SET created_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now().minus(47, ChronoUnit.HOURS)), fresh);

    assertThat(jobs.expirePendingRequests(Instant.now()).result()).isEqualTo(Map.of("expired", 1));
    assertThat(loanStatus(old)).isEqualTo("EXPIRED");
    assertThat(loanStatus(fresh)).isEqualTo("PENDING");

    // Idempotent: a second run changes nothing and does not notify twice.
    assertThat(jobs.expirePendingRequests(Instant.now()).result()).isEqualTo(Map.of("expired", 0));
    Integer notified = jdbc.queryForObject(
        "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = 'LOAN_EXPIRED'", Integer.class, borrower.id());
    assertThat(notified).isEqualTo(1);
  }

  @Test
  @DisplayName("6. a review cannot be submitted twice (full two-step handover and return flow)")
  void duplicateReview() throws Exception {
    TestUser owner = createUser();
    TestUser borrower = createUser();
    String itemId = createItem(owner.id(), "Calculator").id;
    String loanId = json(request(borrower, itemId, 0, 3)).get("id").asText();

    assertThat(step(owner, loanId, "approve", null)).isEqualTo("APPROVED");
    assertThat(step(owner, loanId, "handover", null)).isEqualTo("APPROVED");
    assertThat(step(borrower, loanId, "handover", null)).isEqualTo("ACTIVE");
    assertThat(step(borrower, loanId, "return", null)).isEqualTo("ACTIVE");
    MvcResult returned = perform(post("/api/v1/loans/{id}/return", loanId), owner, Map.of("conditionNote", "All good"));
    assertThat(json(returned).get("status").asText()).isEqualTo("RETURNED");
    assertThat(json(returned).get("returnConditionNote").asText()).isEqualTo("All good");

    MvcResult first = perform(post("/api/v1/loans/{id}/reviews", loanId), borrower, Map.of("rating", 5, "comment", "Great"));
    assertThat(first.getResponse().getStatus()).isEqualTo(201);
    MvcResult second = perform(post("/api/v1/loans/{id}/reviews", loanId), borrower, Map.of("rating", 1));
    assertThat(second.getResponse().getStatus()).isEqualTo(409);
    assertThat(json(second).at("/error/code").asText()).isEqualTo("REVIEW_EXISTS");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE loan_id = ?", Integer.class, loanId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT rating_avg FROM users WHERE id = ?", Double.class, owner.id())).isEqualTo(5.0);

    // Both parties reviewed -> COMPLETED.
    assertThat(perform(post("/api/v1/loans/{id}/reviews", loanId), owner, Map.of("rating", 4)).getResponse().getStatus()).isEqualTo(201);
    assertThat(loanStatus(loanId)).isEqualTo("COMPLETED");
  }

  private String step(TestUser user, String loanId, String path, Object body) throws Exception {
    MvcResult r = perform(post("/api/v1/loans/{id}/" + path, loanId), user, body == null ? Map.of() : body);
    assertThat(r.getResponse().getStatus()).as(path + ": " + r.getResponse().getContentAsString()).isEqualTo(200);
    return json(r).get("status").asText();
  }

  @Test
  @DisplayName("7. a suspended user cannot log in")
  void suspendedCannotLogin() throws Exception {
    TestUser admin = createUser(Role.ADMIN);
    TestUser member = createUser();
    Map<String, String> creds = Map.of("email", member.email(), "password", PASSWORD);
    assertThat(perform(post("/api/v1/auth/login"), null, creds).getResponse().getStatus()).isEqualTo(200);

    MvcResult res = perform(patch("/api/v1/admin/users/{id}/status", member.id()), admin, Map.of("action", "SUSPEND", "reason", "Repeated no-shows"));
    assertThat(res.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(res).get("status").asText()).isEqualTo("SUSPENDED");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'USER_SUSPEND' AND entity_id = ?", Integer.class, member.id()))
        .isEqualTo(1);

    MvcResult login = perform(post("/api/v1/auth/login"), null, creds);
    assertThat(login.getResponse().getStatus()).isEqualTo(403);
    assertThat(json(login).at("/error/code").asText()).isEqualTo("ACCOUNT_SUSPENDED");
    // Existing access tokens stop working immediately too.
    assertThat(perform(get("/api/v1/users/me"), member, null).getResponse().getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("8. two simultaneous approvals for overlapping dates: exactly one succeeds")
  void concurrentApprovals() throws Exception {
    TestUser owner = createUser();
    TestUser a = createUser();
    TestUser b = createUser();
    String itemId = createItem(owner.id(), "Calculator").id;
    String loanA = json(request(a, itemId, 1, 4)).get("id").asText();
    String loanB = json(request(b, itemId, 3, 6)).get("id").asText();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> futures = new ArrayList<>();
    for (String loanId : List.of(loanA, loanB)) {
      Callable<Integer> approve = () -> {
        start.await();
        return perform(post("/api/v1/loans/{id}/approve", loanId), owner, null).getResponse().getStatus();
      };
      futures.add(pool.submit(approve));
    }
    start.countDown();
    List<Integer> statuses = new ArrayList<>();
    for (Future<Integer> f : futures) statuses.add(f.get());
    pool.shutdown();

    assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    List<String> loanStatuses = jdbc.queryForList("SELECT status FROM loans WHERE item_id = ? ORDER BY status", String.class, itemId);
    assertThat(loanStatuses).containsExactly("APPROVED", "REJECTED");

    // The database itself refuses an overlapping booking, even bypassing the service (trigger).
    assertThatThrownBy(() -> insertLoan(itemId, b.id(), LoanStatus.APPROVED, 3, 3)).hasMessageContaining("loans_no_overlap");
  }
}
