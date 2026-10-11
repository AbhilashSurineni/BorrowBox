package com.borrowbox;

import com.borrowbox.common.security.JwtService;
import com.borrowbox.common.seed.DataSeeder;
import com.borrowbox.items.Category;
import com.borrowbox.items.CategoryRepository;
import com.borrowbox.items.Item;
import com.borrowbox.items.ItemCondition;
import com.borrowbox.items.ItemRepository;
import com.borrowbox.loans.LoanStatus;
import com.borrowbox.users.Role;
import com.borrowbox.users.User;
import com.borrowbox.users.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;

/**
 * Spring Boot + MockMvc against a real MySQL 8: Testcontainers by default, or an existing
 * database when TEST_DB_URL (+ TEST_DB_USER / TEST_DB_PASSWORD) is set, as in CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {
  static final MySQLContainer<?> MYSQL;

  static {
    if (System.getenv("TEST_DB_URL") == null) {
      MYSQL = new MySQLContainer<>("mysql:8.0")
          .withDatabaseName("borrowbox_test")
          .withCommand("--log-bin-trust-function-creators=1", "--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");
      MYSQL.start();
    } else {
      MYSQL = null;
    }
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    String params = "useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
    if (MYSQL != null) {
      registry.add("spring.datasource.url", () -> MYSQL.getJdbcUrl() + "?" + params);
      registry.add("spring.datasource.username", MYSQL::getUsername);
      registry.add("spring.datasource.password", MYSQL::getPassword);
    } else {
      String url = System.getenv("TEST_DB_URL");
      registry.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + params);
      registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USER", "borrowbox"));
      registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", "borrowbox"));
    }
  }

  private static final List<String> TABLES = List.of(
      "job_runs", "announcements", "audit_logs", "reports", "notifications", "reviews", "loans", "item_images", "items",
      "categories", "auth_tokens", "refresh_tokens", "settings", "users");
  private static final AtomicInteger COUNTER = new AtomicInteger();
  protected static final String PASSWORD = "Passw0rd123";

  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper mapper;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected UserRepository users;
  @Autowired protected ItemRepository items;
  @Autowired protected CategoryRepository categories;
  @Autowired protected PasswordEncoder encoder;
  @Autowired protected JwtService jwt;
  @Autowired protected DataSeeder seeder;

  @BeforeEach
  void resetDatabase() {
    jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
    TABLES.forEach(t -> jdbc.execute("TRUNCATE TABLE " + t));
    jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    seeder.seedBase();
  }

  protected record TestUser(String id, String email, String token) {
    String bearer() {
      return "Bearer " + token;
    }
  }

  protected TestUser createUser(Role role) {
    int n = COUNTER.incrementAndGet();
    User u = new User();
    u.name = "User " + n;
    u.email = "user" + n + "-" + UUID.randomUUID().toString().substring(0, 8) + "@campus.edu";
    u.passwordHash = encoder.encode(PASSWORD);
    u.department = "Testing";
    u.role = role;
    u.emailVerified = true;
    u.phone = "+1 555 0000";
    users.saveAndFlush(u);
    return new TestUser(u.id, u.email, jwt.signAccessToken(u.id, role.name()));
  }

  protected TestUser createUser() {
    return createUser(Role.MEMBER);
  }

  protected Item createItem(String ownerId, String name) {
    Category books = categories.findByName("Books").orElseThrow();
    Item i = new Item();
    i.ownerId = ownerId;
    i.categoryId = books.id;
    i.name = name;
    i.description = "A graphing calculator for tests";
    i.condition = ItemCondition.GOOD;
    i.pickupLocation = "Library";
    i.maxLoanDays = 14;
    return items.saveAndFlush(i);
  }

  protected static LocalDate today() {
    return LocalDate.now(ZoneOffset.UTC);
  }

  protected static String day(int offset) {
    return today().plusDays(offset).toString();
  }

  /** Inserts a loan directly (bypassing the API) in any status; the overlap triggers still apply. */
  protected String insertLoan(String itemId, String borrowerId, LoanStatus status, int startOffset, int endOffset) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        """
        INSERT INTO loans (id, item_id, borrower_id, status, start_date, end_date, original_end_date, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
        """,
        id, itemId, borrowerId, status.name(), day(startOffset), day(endOffset), day(endOffset));
    return id;
  }

  protected String loanStatus(String loanId) {
    return jdbc.queryForObject("SELECT status FROM loans WHERE id = ?", String.class, loanId);
  }

  protected MvcResult perform(MockHttpServletRequestBuilder req, TestUser user, Object body) throws Exception {
    if (user != null) req.header("Authorization", user.bearer());
    if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
    return mvc.perform(req).andReturn();
  }

  protected JsonNode json(MvcResult r) throws Exception {
    return mapper.readTree(r.getResponse().getContentAsString());
  }
}
