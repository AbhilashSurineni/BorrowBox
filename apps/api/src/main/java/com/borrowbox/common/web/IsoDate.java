package com.borrowbox.common.web;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** A calendar date string in YYYY-MM-DD form (null is allowed; combine with @NotNull). */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IsoDate.Validator.class)
public @interface IsoDate {
  String message() default "Expected YYYY-MM-DD";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator implements ConstraintValidator<IsoDate, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
      if (value == null) return true;
      if (!value.matches("^\\d{4}-\\d{2}-\\d{2}$")) return false;
      try {
        LocalDate.parse(value);
        return true;
      } catch (DateTimeParseException e) {
        return false;
      }
    }
  }
}
