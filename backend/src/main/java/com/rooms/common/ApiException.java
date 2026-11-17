package com.rooms.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
  private final HttpStatus status;
  private final String title;
  private final transient Map<String, Object> extras;

  public ApiException(HttpStatus status, String title, String detail) {
    this(status, title, detail, Map.of());
  }

  public ApiException(HttpStatus status, String title, String detail, Map<String, Object> extras) {
    super(detail);
    this.status = status;
    this.title = title;
    this.extras = extras;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }

  public Map<String, Object> extras() {
    return extras;
  }

  public static ApiException notFound(String what) {
    return new ApiException(HttpStatus.NOT_FOUND, "Not found", what + " was not found.");
  }

  public static ApiException badRequest(String detail) {
    return new ApiException(HttpStatus.BAD_REQUEST, "Invalid request", detail);
  }

  public static ApiException conflict(String title, String detail, Map<String, Object> extras) {
    return new ApiException(HttpStatus.CONFLICT, title, detail, extras);
  }

  public static ApiException forbidden(String detail) {
    return new ApiException(HttpStatus.FORBIDDEN, "Forbidden", detail);
  }
}
