package com.borrowbox.common.errors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Collects validation problems in the same shape Zod's flatten() produced in the TS API:
 * { "formErrors": [...], "fieldErrors": { "field": ["message", ...] } }.
 */
public class ValidationErrors {
  private final List<String> formErrors = new ArrayList<>();
  private final Map<String, List<String>> fieldErrors = new LinkedHashMap<>();

  public ValidationErrors field(String field, String message) {
    fieldErrors.computeIfAbsent(field, k -> new ArrayList<>()).add(message);
    return this;
  }

  public ValidationErrors form(String message) {
    formErrors.add(message);
    return this;
  }

  public boolean isEmpty() {
    return formErrors.isEmpty() && fieldErrors.isEmpty();
  }

  public Map<String, Object> toDetails() {
    return Map.of("formErrors", formErrors, "fieldErrors", fieldErrors);
  }

  public BusinessException toException() {
    return new BusinessException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Invalid request", toDetails());
  }

  public void throwIfAny() {
    if (!isEmpty()) throw toException();
  }

  public static BusinessException single(String field, String message) {
    return new ValidationErrors().field(field, message).toException();
  }
}
