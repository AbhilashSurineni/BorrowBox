package com.borrowbox.common.web;

import com.borrowbox.common.errors.ValidationErrors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Binds a loose map (multipart form fields or a JSON object) to a request record and runs Bean
 * Validation on it, raising the same 400 VALIDATION_ERROR shape as @Valid. Used by endpoints that
 * accept both multipart/form-data and JSON (item create/update, profile update).
 */
@Component
public class RequestBinder {
  private final ObjectMapper mapper;
  private final Validator validator;

  public RequestBinder(ObjectMapper mapper, Validator validator) {
    this.mapper = mapper;
    this.validator = validator;
  }

  public <T> T bind(Map<String, ?> values, Class<T> type) {
    T value;
    try {
      value = mapper.convertValue(values, type);
    } catch (IllegalArgumentException e) {
      ValidationErrors errors = new ValidationErrors();
      if (e.getCause() instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
        errors.field(mie.getPath().get(mie.getPath().size() - 1).getFieldName(), "Invalid value");
      } else {
        errors.form("Invalid request");
      }
      throw errors.toException();
    }
    validate(value);
    return value;
  }

  public <T> void validate(T value) {
    Set<ConstraintViolation<T>> violations = validator.validate(value);
    if (violations.isEmpty()) return;
    ValidationErrors errors = new ValidationErrors();
    violations.forEach(v -> {
      String path = v.getPropertyPath().toString();
      if (path.isEmpty()) errors.form(v.getMessage());
      else errors.field(path, v.getMessage());
    });
    throw errors.toException();
  }

  /** Multipart fields as a map; multi-valued fields keep their first value. */
  public static Map<String, Object> formFields(Map<String, String[]> params) {
    Map<String, Object> out = new LinkedHashMap<>();
    params.forEach((k, v) -> {
      if (v != null && v.length > 0) out.put(k, v.length == 1 ? v[0] : v);
    });
    return out;
  }
}
