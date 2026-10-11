package com.borrowbox.common.seed;

import com.borrowbox.common.audit.AuditService;
import com.borrowbox.common.config.AppProperties;
import com.borrowbox.items.CategoryRepository;
import com.borrowbox.items.Item;
import com.borrowbox.items.ItemCondition;
import com.borrowbox.items.ItemRepository;
import com.borrowbox.loans.Loan;
import com.borrowbox.loans.LoanRepository;
import com.borrowbox.loans.LoanStatus;
import com.borrowbox.notifications.Notification;
import com.borrowbox.notifications.NotificationRepository;
import com.borrowbox.reports.Report;
import com.borrowbox.reports.ReportReason;
import com.borrowbox.reports.ReportRepository;
import com.borrowbox.reports.ReportTargetType;
import com.borrowbox.reviews.Review;
import com.borrowbox.reviews.ReviewRepository;
import com.borrowbox.users.ContactPreference;
import com.borrowbox.users.User;
import com.borrowbox.users.UserRepository;
import com.borrowbox.common.web.Ids;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Dev-only demo data (users alice|bob|carol|dan, items, loans in every state). Idempotent. */
@Component
@Profile("dev")
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
  static final String DEMO_PASSWORD = "Demo12345";

  private final AppProperties props;
  private final UserRepository users;
  private final CategoryRepository categories;
  private final ItemRepository items;
  private final LoanRepository loans;
  private final ReviewRepository reviews;
  private final NotificationRepository notifications;
  private final ReportRepository reports;
  private final AuditService audit;
  private final PasswordEncoder encoder;

  public DemoDataSeeder(
      AppProperties props,
      UserRepository users,
      CategoryRepository categories,
      ItemRepository items,
      LoanRepository loans,
      ReviewRepository reviews,
      NotificationRepository notifications,
      ReportRepository reports,
      AuditService audit,
      PasswordEncoder encoder) {
    this.props = props;
    this.users = users;
    this.categories = categories;
    this.items = items;
    this.loans = loans;
    this.reviews = reviews;
    this.notifications = notifications;
    this.reports = reports;
    this.audit = audit;
    this.encoder = encoder;
  }

  private static LocalDate day(int offset) {
    return LocalDate.now(ZoneOffset.UTC).plusDays(offset);
  }

  private static Instant at(int offset) {
    return day(offset).atStartOfDay().toInstant(ZoneOffset.UTC);
  }

  private User user(String email, String name, String department, String phone) {
    return users.findByEmail(email).orElseGet(() -> {
      User u = new User();
      u.email = email;
      u.name = name;
      u.department = department;
      u.phone = phone;
      u.emailVerified = true;
      u.contactPreference = phone != null ? ContactPreference.BOTH : ContactPreference.EMAIL;
      u.passwordHash = encoder.encode(DEMO_PASSWORD);
      return users.save(u);
    });
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!props.seed().demoData()) return;
    String domain = props.allowedEmailDomainList().isEmpty() ? "campus.edu" : props.allowedEmailDomainList().get(0);
    User alice = user("alice@" + domain, "Alice Mensah", "Physics", "+1 555 0101");
    User bob = user("bob@" + domain, "Bob Okafor", "Computer Science", null);
    User carol = user("carol@" + domain, "Carol Singh", "Chemistry", "+1 555 0103");
    User dan = user("dan@" + domain, "Dan Ito", "Mechanical Engineering", null);
    if (!items.findByOwnerId(alice.id).isEmpty() || !items.findByOwnerId(bob.id).isEmpty()) return;

    Map<String, String> cat = categories.findAll().stream().collect(Collectors.toMap(c -> c.name, c -> c.id));
    Function<Object[], Item> mk = a -> {
      Item i = new Item();
      i.ownerId = ((User) a[0]).id;
      i.categoryId = cat.get((String) a[1]);
      i.name = (String) a[2];
      i.description = (String) a[3];
      i.condition = (ItemCondition) a[4];
      i.pickupLocation = (String) a[5];
      i.maxLoanDays = (Integer) a[6];
      return items.save(i);
    };
    Item calc = mk.apply(new Object[] {alice, "Calculators", "TI-84 Plus graphing calculator", "Works perfectly, comes with a cover. Fresh batteries.", ItemCondition.GOOD, "Physics building lobby", 7});
    Item labCoat = mk.apply(new Object[] {alice, "Lab Equipment", "Lab coat (size M)", "Clean white lab coat, size M. Washed after every loan.", ItemCondition.GOOD, "Physics building lobby", 14});
    Item physicsBook = mk.apply(new Object[] {alice, "Books", "University Physics, 15th edition", "Young & Freedman. A few highlighted pages.", ItemCondition.FAIR, "Main library entrance", 21});
    Item racket = mk.apply(new Object[] {bob, "Sports Equipment", "Badminton racket set", "Two rackets and three shuttlecocks.", ItemCondition.GOOD, "Sports centre reception", 5});
    Item arduino = mk.apply(new Object[] {bob, "Electronics", "Arduino Uno starter kit", "Board, breadboard, jumper wires, LEDs and sensors.", ItemCondition.NEW, "CS lab 2.14", 14});
    Item drill = mk.apply(new Object[] {carol, "Tools", "Cordless drill", "Includes two batteries and a drill bit set.", ItemCondition.WORN, "Chemistry block, room 104", 3});
    Item goggles = mk.apply(new Object[] {carol, "Lab Equipment", "Safety goggles", "Anti-fog safety goggles, adjustable strap.", ItemCondition.NEW, "Chemistry block, room 104", 14});
    mk.apply(new Object[] {dan, "Stationery", "Engineering drawing set", "Compass, set squares and protractor.", ItemCondition.GOOD, "Engineering workshop", 14});
    goggles.isPaused = true;
    items.save(goggles);

    Loan pending = loan(calc, bob, LoanStatus.PENDING, 2, 5);
    pending.message = "Need it for the stats midterm.";
    loans.save(pending);
    Loan reserved = loan(labCoat, carol, LoanStatus.APPROVED, 3, 6);
    reserved.approvedAt = Instant.now();
    loans.save(reserved);
    Loan active = loan(arduino, alice, LoanStatus.ACTIVE, -2, 4);
    handedOver(active, -3, -2);
    Loan overdue = loan(drill, dan, LoanStatus.OVERDUE, -6, -3);
    handedOver(overdue, -7, -6);
    Loan done = loan(physicsBook, bob, LoanStatus.COMPLETED, -30, -20);
    handedOver(done, -31, -30);
    done.returnMarkedById = bob.id;
    done.returnMarkedAt = at(-20);
    done.returnedAt = at(-20);
    done.returnConditionNote = "Same condition as lent.";
    done.completedAt = at(-15);
    loans.save(done);
    Loan returned = loan(racket, carol, LoanStatus.RETURNED, -5, -2);
    handedOver(returned, -6, -5);
    returned.returnMarkedById = bob.id;
    returned.returnMarkedAt = at(-2);
    returned.returnedAt = at(-2);
    loans.save(returned);

    review(done, bob, alice, 5, "Great book, easy pickup.");
    review(done, alice, bob, 4, "Returned on time.");
    alice.ratingAvg = new BigDecimal("5.00");
    alice.ratingCount = 1;
    bob.ratingAvg = new BigDecimal("4.00");
    bob.ratingCount = 1;
    users.saveAll(List.of(alice, bob));
    physicsBook.ratingAvg = new BigDecimal("5.00");
    physicsBook.ratingCount = 1;
    items.save(physicsBook);

    Notification n = new Notification();
    n.id = Ids.newId();
    n.userId = alice.id;
    n.type = "LOAN_REQUESTED";
    n.title = "New request for " + calc.name;
    n.body = bob.name + " wants to borrow " + calc.name + ".";
    n.link = "/loans/" + pending.id;
    n.createdAt = Instant.now();
    notifications.save(n);

    Report r = new Report();
    r.reporterId = carol.id;
    r.targetType = ReportTargetType.USER;
    r.targetId = dan.id;
    r.targetUserId = dan.id;
    r.reason = ReportReason.NOT_RETURNED;
    r.description = "The drill is three days late and Dan is not answering.";
    reports.save(r);

    User admin = props.seed().adminEmail() == null ? null : users.findByEmail(props.seed().adminEmail().toLowerCase()).orElse(null);
    audit.log(admin == null ? null : admin.id, "SEED", "System", null, Map.of("demo", true));
    log.info("Seeded demo data. Demo users: alice|bob|carol|dan@{} / {}", domain, DEMO_PASSWORD);
  }

  private Loan loan(Item item, User borrower, LoanStatus status, int start, int end) {
    Loan l = new Loan();
    l.itemId = item.id;
    l.borrowerId = borrower.id;
    l.status = status;
    l.startDate = day(start);
    l.endDate = day(end);
    l.originalEndDate = day(end);
    return loans.save(l);
  }

  private void handedOver(Loan l, int approved, int handover) {
    l.approvedAt = at(approved);
    l.handoverMarkedAt = at(handover);
    l.handedOverAt = at(handover);
    loans.save(l);
  }

  private void review(Loan loan, User reviewer, User reviewee, int rating, String comment) {
    Review r = new Review();
    r.loanId = loan.id;
    r.reviewerId = reviewer.id;
    r.revieweeId = reviewee.id;
    r.rating = rating;
    r.comment = comment;
    reviews.save(r);
  }

}
