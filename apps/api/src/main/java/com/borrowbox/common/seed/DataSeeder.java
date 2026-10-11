package com.borrowbox.common.seed;

import com.borrowbox.common.config.AppProperties;
import com.borrowbox.common.settings.SettingsService;
import com.borrowbox.items.Category;
import com.borrowbox.items.CategoryRepository;
import com.borrowbox.users.Role;
import com.borrowbox.users.User;
import com.borrowbox.users.UserRepository;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Idempotent startup seed: the 8 categories, default settings and the admin from ADMIN_EMAIL/ADMIN_PASSWORD. */
@Component
@Order(1)
public class DataSeeder implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

  static final List<String[]> CATEGORIES = List.of(
      new String[] {"Books", "book"},
      new String[] {"Calculators", "calculator"},
      new String[] {"Lab Equipment", "flask"},
      new String[] {"Sports Equipment", "ball"},
      new String[] {"Electronics", "cpu"},
      new String[] {"Stationery", "pencil"},
      new String[] {"Tools", "wrench"},
      new String[] {"Other", "box"});

  private final CategoryRepository categories;
  private final SettingsService settings;
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final AppProperties props;

  public DataSeeder(CategoryRepository categories, SettingsService settings, UserRepository users, PasswordEncoder encoder, AppProperties props) {
    this.categories = categories;
    this.settings = settings;
    this.users = users;
    this.encoder = encoder;
    this.props = props;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    seedBase();
  }

  @Transactional
  public void seedBase() {
    for (String[] c : CATEGORIES) {
      if (categories.findByName(c[0]).isPresent()) continue;
      Category cat = new Category();
      cat.name = c[0];
      cat.icon = c[1];
      categories.save(cat);
    }
    settings.seedDefaults();
    String email = props.seed().adminEmail();
    String password = props.seed().adminPassword();
    if (StringUtils.hasText(email) && StringUtils.hasText(password)) {
      String normalized = email.trim().toLowerCase(Locale.ROOT);
      if (users.findByEmail(normalized).isEmpty()) {
        User admin = new User();
        admin.email = normalized;
        admin.name = "BorrowBox Admin";
        admin.department = "Student Services";
        admin.role = Role.ADMIN;
        admin.emailVerified = true;
        admin.passwordHash = encoder.encode(password);
        users.save(admin);
        log.info("Seeded admin account {}", normalized);
      }
    }
  }
}
