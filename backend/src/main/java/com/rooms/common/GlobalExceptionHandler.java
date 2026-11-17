package com.rooms.common;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private static ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String title, String detail, Map<String, Object> extras) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
    pd.setTitle(title);
    extras.forEach(pd::setProperty);
    return ResponseEntity.status(status)
        .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
        .body(pd);
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> api(ApiException e) {
    return problem(e.status(), e.title(), e.getMessage(), e.extras());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException e) {
    Map<String, String> errors = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
    return problem(
        HttpStatus.BAD_REQUEST,
        "Invalid request",
        "Some fields are not valid. Fix them and try again.",
        Map.of("errors", errors));
  }

  @ExceptionHandler({
    ConstraintViolationException.class,
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ProblemDetail> badInput(Exception e) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "Invalid request",
        "The request could not be read. Check the values and try again.",
        Map.of());
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  ResponseEntity<ProblemDetail> stale(OptimisticLockingFailureException e) {
    return problem(
        HttpStatus.CONFLICT,
        "Stale update",
        "This booking was changed by someone else. Reload it and try again.",
        Map.of());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> denied(AccessDeniedException e) {
    return problem(
        HttpStatus.FORBIDDEN, "Forbidden", "You do not have access to this resource.", Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> other(Exception e) {
    log.error("Unhandled error of type {}", e.getClass().getSimpleName(), e);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Server error",
        "Something went wrong on our side. Try again in a moment.",
        Map.of());
  }
}
