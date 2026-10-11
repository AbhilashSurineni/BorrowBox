package com.borrowbox.common.web;

import java.util.regex.Pattern;

/**
 * Strips HTML tags and control characters from user-generated text and trims it (defence in
 * depth against XSS; the frontend renders text escaped). Same rules as the TS sanitizeText.
 */
public final class Sanitizer {
  private static final Pattern TAGS = Pattern.compile("<[^>]*>?");
  private static final Pattern CONTROL = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

  private Sanitizer() {}

  public static String clean(String input) {
    if (input == null) return null;
    return CONTROL.matcher(TAGS.matcher(input).replaceAll("")).replaceAll("").trim();
  }

  /** Sanitises and maps blank to null (for optional fields). */
  public static String cleanOptional(String input) {
    String s = clean(input);
    return s == null || s.isEmpty() ? null : s;
  }
}
