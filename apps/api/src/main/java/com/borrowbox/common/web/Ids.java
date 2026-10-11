package com.borrowbox.common.web;

import com.borrowbox.common.errors.ValidationErrors;
import java.util.UUID;
import java.util.regex.Pattern;

public final class Ids {
  private static final Pattern UUID_RE =
      Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

  private Ids() {}

  public static String newId() {
    return UUID.randomUUID().toString();
  }

  public static boolean isUuid(String s) {
    return s != null && UUID_RE.matcher(s).matches();
  }

  /** Validates a path id like the TS idParam schema: 400 VALIDATION_ERROR for non-UUIDs. */
  public static String require(String id) {
    if (!isUuid(id)) throw ValidationErrors.single("id", "Invalid uuid");
    return id.toLowerCase();
  }
}
