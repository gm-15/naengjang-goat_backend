package com.naengjang_goat.inventory_system.workflow;

import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
  public record ErrorBody(int status, String message) {}

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ErrorBody> response(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(new ErrorBody(e.getStatusCode().value(), e.getReason()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest()
        .body(
            new ErrorBody(
                400,
                e.getBindingResult().getFieldErrors().stream()
                    .map(x -> x.getField() + ": " + x.getDefaultMessage())
                    .findFirst()
                    .orElse("입력값을 확인하세요")));
  }

  @ExceptionHandler({IllegalArgumentException.class, MaxUploadSizeExceededException.class})
  public ResponseEntity<ErrorBody> bad(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            new ErrorBody(
                400,
                e instanceof MaxUploadSizeExceededException
                    ? "파일은 5MB 이하로 올려주세요"
                    : e.getMessage()));
  }

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<ErrorBody> missing(NoSuchElementException e) {
    return ResponseEntity.status(404).body(new ErrorBody(404, e.getMessage()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorBody> duplicate(DataIntegrityViolationException e) {
    return ResponseEntity.status(409).body(new ErrorBody(409, "이미 반영된 데이터이거나 입력 제약을 위반했습니다"));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorBody> state(IllegalStateException e) {
    return ResponseEntity.status(409).body(new ErrorBody(409, e.getMessage()));
  }
}
