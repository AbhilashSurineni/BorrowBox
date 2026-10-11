package com.borrowbox.common.errors;

import org.springframework.http.HttpStatus;

/** An expected, user-facing failure: HTTP status + ErrorCode + message (+ optional details). */
public class BusinessException extends RuntimeException {
  private final HttpStatus status;
  private final ErrorCode code;
  private final transient Object details;

  public BusinessException(HttpStatus status, ErrorCode code, String message) {
    this(status, code, message, null);
  }

  public BusinessException(HttpStatus status, ErrorCode code, String message, Object details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }

  public HttpStatus status() {
    return status;
  }

  public ErrorCode code() {
    return code;
  }

  public Object details() {
    return details;
  }

  public static BusinessException badRequest(ErrorCode code, String message) {
    return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
  }

  public static BusinessException unauthorized(ErrorCode code, String message) {
    return new BusinessException(HttpStatus.UNAUTHORIZED, code, message);
  }

  public static BusinessException forbidden(String message) {
    return new BusinessException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, message);
  }

  public static BusinessException forbidden(ErrorCode code, String message) {
    return new BusinessException(HttpStatus.FORBIDDEN, code, message);
  }

  public static BusinessException notFound(String what) {
    return new BusinessException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, what + " not found");
  }

  public static BusinessException conflict(ErrorCode code, String message) {
    return new BusinessException(HttpStatus.CONFLICT, code, message);
  }

  public static BusinessException unprocessable(ErrorCode code, String message) {
    return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
  }

  public static BusinessException tooMany(ErrorCode code, String message) {
    return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, code, message);
  }

  public static BusinessException datesUnavailable() {
    return conflict(ErrorCode.DATES_UNAVAILABLE, "The item is already booked for some of these dates");
  }
}
