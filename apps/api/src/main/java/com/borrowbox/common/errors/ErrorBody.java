package com.borrowbox.common.errors;

import com.fasterxml.jackson.annotation.JsonInclude;

/** { "error": { "code": "...", "message": "...", "details"?: ... } } */
public record ErrorBody(Error error) {
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Error(String code, String message, Object details) {}

  public static ErrorBody of(String code, String message, Object details) {
    return new ErrorBody(new Error(code, message, details));
  }

  public static ErrorBody of(ErrorCode code, String message) {
    return of(code.name(), message, null);
  }
}
