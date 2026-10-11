package com.borrowbox.common.errors;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps every failure to the TS API's error body, codes and HTTP statuses. */
@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private static ResponseEntity<ErrorBody> send(HttpStatus status, String code, String message, Object details) {
    return ResponseEntity.status(status).body(ErrorBody.of(code, message, details));
  }

  private static ResponseEntity<ErrorBody> send(HttpStatus status, ErrorCode code, String message) {
    return send(status, code.name(), message, null);
  }

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorBody> business(BusinessException e) {
    return send(e.status(), e.code().name(), e.getMessage(), e.details());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException e) {
    ValidationErrors errors = new ValidationErrors();
    e.getBindingResult().getFieldErrors().forEach(f -> errors.field(f.getField(), f.getDefaultMessage()));
    e.getBindingResult().getGlobalErrors().forEach(g -> errors.form(g.getDefaultMessage()));
    return business(errors.toException());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException e) {
    Throwable cause = e.getMostSpecificCause();
    if (cause instanceof MismatchedInputException mie && !(cause instanceof JsonParseException)) {
      String field = mie.getPath().isEmpty() ? null : mie.getPath().get(mie.getPath().size() - 1).getFieldName();
      ValidationErrors errors = new ValidationErrors();
      if (field != null) errors.field(field, "Invalid value");
      else errors.form("Invalid request body");
      return business(errors.toException());
    }
    if (e.getMessage() != null && e.getMessage().startsWith("Required request body is missing")) {
      return business(new ValidationErrors().form("Request body is required").toException());
    }
    return send(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Malformed JSON body");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorBody> tooLarge(MaxUploadSizeExceededException e) {
    return send(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.INVALID_IMAGE, "Each image must be 5 MB or smaller");
  }

  @ExceptionHandler(MultipartException.class)
  public ResponseEntity<ErrorBody> multipart(MultipartException e) {
    return send(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Malformed multipart body");
  }

  @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
  public ResponseEntity<ErrorBody> denied(Exception e) {
    return send(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Insufficient role");
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ErrorBody> optimistic(OptimisticLockingFailureException e) {
    return send(HttpStatus.CONFLICT, ErrorCode.INVALID_TRANSITION, "The loan changed in the meantime; refresh and try again");
  }

  @ExceptionHandler({NoResourceFoundException.class})
  public ResponseEntity<ErrorBody> noRoute(HttpServletRequest req) {
    return send(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Route " + req.getMethod() + " " + req.getRequestURI() + " not found");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorBody> method(HttpServletRequest req) {
    return noRoute(req);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorBody> mediaType(HttpMediaTypeNotSupportedException e) {
    return send(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Unsupported content type");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorBody> other(Exception e) {
    if (isOverlapViolation(e)) {
      return business(BusinessException.datesUnavailable());
    }
    if (e instanceof DataIntegrityViolationException && hasCause(e, SQLIntegrityConstraintViolationException.class)) {
      return send(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "Resource already exists");
    }
    log.error("Unhandled error", e);
    return send(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL, "Something went wrong");
  }

  /** The loans_no_overlap triggers SIGNAL SQLSTATE '45000' (see V2 migration). */
  public static boolean isOverlapViolation(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof SQLException sql && "45000".equals(sql.getSQLState())) return true;
      if (t.getMessage() != null && t.getMessage().contains("loans_no_overlap")) return true;
      if (t.getCause() == t) break;
    }
    return false;
  }

  private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (type.isInstance(t)) return true;
      if (t.getCause() == t) break;
    }
    return false;
  }
}
