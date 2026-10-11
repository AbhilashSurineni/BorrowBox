package com.borrowbox.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Aggregate queries for the admin dashboard charts (MySQL). */
@Repository
public class AdminStatsRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public AdminStatsRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record WeekCount(long requested, long approved) {}

  public record Ranked(String id, String name, double ratingAvg, long loans) {}

  /** Loans created per ISO week (Monday start), keyed by the week's Monday. */
  public Map<LocalDate, WeekCount> loansPerWeek(Instant since) {
    Map<LocalDate, WeekCount> out = new HashMap<>();
    jdbc.query(
        """
        SELECT DATE_SUB(DATE(created_at), INTERVAL WEEKDAY(created_at) DAY) AS week,
               COUNT(*) AS requested,
               SUM(approved_at IS NOT NULL) AS approved
        FROM loans WHERE created_at >= :since
        GROUP BY week
        """,
        new MapSqlParameterSource("since", java.sql.Timestamp.from(since)),
        rs -> {
          out.put(rs.getObject("week", LocalDate.class), new WeekCount(rs.getLong("requested"), rs.getLong("approved")));
        });
    return out;
  }

  public List<Ranked> topCategories() {
    return jdbc.query(
        """
        SELECT c.id, c.name, COUNT(l.id) AS loans
        FROM categories c
        JOIN items i ON i.category_id = c.id
        JOIN loans l ON l.item_id = i.id AND l.status IN ('APPROVED','ACTIVE','OVERDUE','RETURNED','COMPLETED')
        GROUP BY c.id, c.name ORDER BY loans DESC, c.name LIMIT 5
        """,
        (rs, n) -> new Ranked(rs.getString("id"), rs.getString("name"), 0, rs.getLong("loans")));
  }

  public List<Ranked> topLenders() {
    return jdbc.query(
        """
        SELECT u.id, u.name, u.rating_avg, COUNT(l.id) AS loans
        FROM users u
        JOIN items i ON i.owner_id = u.id
        JOIN loans l ON l.item_id = i.id AND l.status IN ('ACTIVE','OVERDUE','RETURNED','COMPLETED')
        WHERE u.deleted_at IS NULL
        GROUP BY u.id, u.name, u.rating_avg ORDER BY loans DESC, u.name LIMIT 5
        """,
        (rs, n) -> new Ranked(rs.getString("id"), rs.getString("name"), rs.getBigDecimal("rating_avg").doubleValue(), rs.getLong("loans")));
  }
}
