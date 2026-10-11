package com.borrowbox.items;

import com.borrowbox.items.ItemViews.CategoryRef;
import com.borrowbox.items.ItemViews.ItemSummary;
import com.borrowbox.users.UserViews.PublicUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * FR-9..11 browse query, one parameterised MySQL statement: FULLTEXT keyword search, filters,
 * derived status and "soonest available" computed in SQL via a LATERAL derived table (no N+1).
 */
@Repository
public class ItemSearchRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public ItemSearchRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Criteria(
      String q,
      String category,
      ItemCondition condition,
      Boolean available,
      LocalDate availableFrom,
      LocalDate availableTo,
      String location,
      Integer maxDuration,
      Double minRating,
      String sort,
      int offset,
      int limit) {}

  public record Result(List<ItemSummary> data, long total) {}

  private static String escapeLike(String s) {
    return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  public Result search(Criteria c, LocalDate today) {
    MapSqlParameterSource p = new MapSqlParameterSource("today", today);
    List<String> conds = new ArrayList<>(List.of(
        "i.is_removed = 0", "i.deleted_at IS NULL", "c.is_active = 1", "u.status = 'ACTIVE'", "u.deleted_at IS NULL"));

    if (c.q() != null) {
      conds.add("MATCH(i.name, i.description) AGAINST (:q IN NATURAL LANGUAGE MODE)");
      p.addValue("q", c.q());
    }
    if (c.category() != null) {
      conds.add("i.category_id = :category");
      p.addValue("category", c.category());
    }
    if (c.condition() != null) {
      conds.add("i.item_condition = :condition");
      p.addValue("condition", c.condition().name());
    }
    if (c.location() != null) {
      conds.add("i.pickup_location LIKE :location");
      p.addValue("location", "%" + escapeLike(c.location()) + "%");
    }
    if (c.maxDuration() != null) {
      conds.add("i.max_loan_days >= :maxDuration");
      p.addValue("maxDuration", c.maxDuration());
    }
    if (c.minRating() != null) {
      conds.add("u.rating_avg >= :minRating");
      p.addValue("minRating", c.minRating());
    }
    if (Boolean.TRUE.equals(c.available())) {
      conds.add("i.is_paused = 0 AND COALESCE(st.on_loan, 0) = 0");
      conds.add("""
          NOT EXISTS (SELECT 1 FROM loans l WHERE l.item_id = i.id AND l.status = 'APPROVED'
            AND l.start_date <= :today AND l.end_date >= :today)""");
    }
    if (c.availableFrom() != null || c.availableTo() != null) {
      LocalDate from = c.availableFrom() != null ? c.availableFrom() : c.availableTo();
      LocalDate to = c.availableTo() != null ? c.availableTo() : from;
      conds.add("""
          i.is_paused = 0 AND NOT EXISTS (
            SELECT 1 FROM loans l
            WHERE l.item_id = i.id
              AND l.status IN ('APPROVED', 'ACTIVE', 'OVERDUE')
              AND l.start_date <= :availTo
              AND (CASE WHEN l.status = 'OVERDUE' THEN GREATEST(l.end_date, :today) ELSE l.end_date END) >= :availFrom)""");
      p.addValue("availFrom", from);
      p.addValue("availTo", to);
    }

    String from = """
        FROM items i
        JOIN categories c ON c.id = i.category_id
        JOIN users u ON u.id = i.owner_id
        LEFT JOIN LATERAL (
          SELECT MAX(l.status IN ('ACTIVE', 'OVERDUE')) AS on_loan,
                 MAX(l.status = 'APPROVED') AS reserved,
                 CASE WHEN MAX(l.status = 'OVERDUE') = 1 THEN NULL
                      ELSE CAST(GREATEST(CAST(:today AS DATE), COALESCE(DATE_ADD(MAX(CASE WHEN l.start_date <= :today THEN l.end_date END), INTERVAL 1 DAY), CAST(:today AS DATE))) AS DATE)
                 END AS next_available
          FROM loans l
          WHERE l.item_id = i.id
            AND l.status IN ('APPROVED', 'ACTIVE', 'OVERDUE')
            AND (l.end_date >= :today OR l.status = 'OVERDUE')
        ) st ON TRUE
        WHERE""" + " " + String.join(" AND ", conds);

    // With a keyword, relevance ranks results (primary for "newest", tie-breaker otherwise).
    String relevance = c.q() != null ? "MATCH(i.name, i.description) AGAINST (:q IN NATURAL LANGUAGE MODE) DESC, " : "";
    String orderBy = switch (c.sort()) {
      case "rating" -> "ORDER BY i.rating_avg DESC, u.rating_avg DESC, " + relevance + "i.created_at DESC";
      case "soonest_available" ->
          "ORDER BY (CASE WHEN i.is_paused = 1 THEN NULL ELSE st.next_available END) IS NULL, "
              + "(CASE WHEN i.is_paused = 1 THEN NULL ELSE st.next_available END) ASC, " + relevance + "i.created_at DESC";
      default -> "ORDER BY " + relevance + "i.created_at DESC";
    };

    p.addValue("limit", c.limit());
    p.addValue("offset", c.offset());
    String select = """
        SELECT i.id, i.name, i.description, i.item_condition, i.max_loan_days, i.pickup_location, i.is_paused,
               i.rating_avg, i.rating_count, i.created_at,
               c.id AS category_id, c.name AS category_name, c.icon AS category_icon,
               u.id AS owner_id, u.name AS owner_name, u.department AS owner_department,
               u.avatar_url AS owner_avatar_url, u.rating_avg AS owner_rating_avg, u.rating_count AS owner_rating_count,
               (SELECT im.url FROM item_images im WHERE im.item_id = i.id ORDER BY im.position LIMIT 1) AS cover_url,
               st.on_loan, st.reserved, st.next_available
        """;
    List<ItemSummary> rows = jdbc.query(select + from + " " + orderBy + " LIMIT :limit OFFSET :offset", p, ItemSearchRepository::map);
    Long total = jdbc.queryForObject("SELECT COUNT(*) " + from, p, Long.class);
    return new Result(rows, total == null ? 0 : total);
  }

  private static ItemSummary map(ResultSet rs, int n) throws SQLException {
    boolean paused = rs.getBoolean("is_paused");
    boolean onLoan = rs.getInt("on_loan") == 1;
    boolean reserved = rs.getInt("reserved") == 1;
    ItemStatus status = paused ? ItemStatus.PAUSED : onLoan ? ItemStatus.ON_LOAN : reserved ? ItemStatus.RESERVED : ItemStatus.AVAILABLE;
    LocalDate next = rs.getObject("next_available", LocalDate.class);
    String description = rs.getString("description");
    if (description.length() > 200) description = description.substring(0, 200) + "…";
    return new ItemSummary(
        rs.getString("id"),
        rs.getString("name"),
        description,
        ItemCondition.valueOf(rs.getString("item_condition")),
        rs.getInt("max_loan_days"),
        rs.getString("pickup_location"),
        rs.getBigDecimal("rating_avg").doubleValue(),
        rs.getInt("rating_count"),
        rs.getString("cover_url"),
        status,
        paused ? null : next,
        new CategoryRef(rs.getString("category_id"), rs.getString("category_name"), rs.getString("category_icon")),
        new PublicUser(
            rs.getString("owner_id"),
            rs.getString("owner_name"),
            rs.getString("owner_department"),
            rs.getString("owner_avatar_url"),
            rs.getBigDecimal("owner_rating_avg").doubleValue(),
            rs.getInt("owner_rating_count")),
        rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC));
  }
}
