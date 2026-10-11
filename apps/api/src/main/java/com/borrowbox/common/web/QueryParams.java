package com.borrowbox.common.web;

import com.borrowbox.common.errors.BusinessException;
import com.borrowbox.common.errors.ValidationErrors;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Typed, validated access to query-string parameters. Blank values count as absent (as in the
 * TS API); problems are collected and raised together as 400 VALIDATION_ERROR via {@link #done()}.
 */
public class QueryParams {
  private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
  private final Map<String, String> params;
  private final ValidationErrors errors = new ValidationErrors();

  public QueryParams(Map<String, String> params) {
    this.params = params;
  }

  private String raw(String key) {
    String v = params.get(key);
    return v == null || v.isEmpty() ? null : v;
  }

  public String string(String key, int max) {
    String v = raw(key);
    if (v == null) return null;
    v = v.trim();
    if (v.length() > max) errors.field(key, "String must contain at most " + max + " character(s)");
    return v.isEmpty() ? null : v;
  }

  public String uuid(String key) {
    String v = raw(key);
    if (v != null && !Ids.isUuid(v)) {
      errors.field(key, "Invalid uuid");
      return null;
    }
    return v;
  }

  public <E extends Enum<E>> E enumValue(String key, Class<E> type) {
    String v = raw(key);
    if (v == null) return null;
    try {
      return Enum.valueOf(type, v);
    } catch (IllegalArgumentException e) {
      errors.field(key, "Invalid enum value '" + v + "'");
      return null;
    }
  }

  /** Comma-separated, case-insensitive list of enum values. */
  public <E extends Enum<E>> List<E> enumList(String key, Class<E> type) {
    String v = raw(key);
    if (v == null) return null;
    List<E> out = new ArrayList<>();
    for (String part : v.split(",")) {
      String s = part.trim().toUpperCase(Locale.ROOT);
      try {
        out.add(Enum.valueOf(type, s));
      } catch (IllegalArgumentException e) {
        errors.field(key, "Invalid enum value '" + s + "'");
      }
    }
    return out;
  }

  /** "true"/"1" and "false"/"0". */
  public Boolean bool(String key) {
    String v = raw(key);
    if (v == null) return null;
    return switch (v) {
      case "true", "1" -> true;
      case "false", "0" -> false;
      default -> {
        errors.field(key, "Expected boolean, received string");
        yield null;
      }
    };
  }

  public LocalDate date(String key) {
    return parseDate(raw(key), key, errors);
  }

  public Integer integer(String key, int min, int max) {
    String v = raw(key);
    if (v == null) return null;
    try {
      int n = Integer.parseInt(v.trim());
      if (n < min) errors.field(key, "Number must be greater than or equal to " + min);
      else if (n > max) errors.field(key, "Number must be less than or equal to " + max);
      return n;
    } catch (NumberFormatException e) {
      errors.field(key, "Expected integer, received " + v);
      return null;
    }
  }

  public Double decimal(String key, double min, double max) {
    String v = raw(key);
    if (v == null) return null;
    try {
      double n = Double.parseDouble(v.trim());
      if (n < min || n > max) errors.field(key, "Number must be between " + min + " and " + max);
      return n;
    } catch (NumberFormatException e) {
      errors.field(key, "Expected number, received " + v);
      return null;
    }
  }

  public PageQuery page() {
    Integer page = integer("page", 1, Integer.MAX_VALUE);
    Integer limit = integer("limit", 1, 100);
    return new PageQuery(page == null ? 1 : page, limit == null ? 20 : limit);
  }

  public ValidationErrors errors() {
    return errors;
  }

  /** Throws 400 VALIDATION_ERROR if any parameter was invalid. */
  public void done() {
    errors.throwIfAny();
  }

  public static LocalDate parseDate(String v, String field, ValidationErrors errors) {
    if (v == null) return null;
    if (!DATE.matcher(v).matches()) {
      errors.field(field, "Expected YYYY-MM-DD");
      return null;
    }
    try {
      return LocalDate.parse(v);
    } catch (DateTimeParseException e) {
      errors.field(field, "Invalid date");
      return null;
    }
  }

  public static BusinessException invalid(String field, String message) {
    return ValidationErrors.single(field, message);
  }
}
