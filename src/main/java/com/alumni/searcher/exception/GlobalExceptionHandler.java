package com.alumni.searcher.exception;

import com.alumni.searcher.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return reply(HttpStatus.BAD_REQUEST, ApiResponse.error("Some fields are missing or invalid", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        return reply(HttpStatus.BAD_REQUEST, ApiResponse.error(
                "Request body is missing or is not valid JSON. Pass-out year must be a number."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleWrongContentType(HttpMediaTypeNotSupportedException e) {
        return reply(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ApiResponse.error("Please send the request body as JSON (Content-Type: application/json)."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleWrongMethod(HttpRequestMethodNotSupportedException e) {
        return reply(HttpStatus.METHOD_NOT_ALLOWED,
                ApiResponse.error("The " + e.getMethod() + " method is not supported for this URL."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknownUrl(NoResourceFoundException e) {
        return reply(HttpStatus.NOT_FOUND, ApiResponse.error("No API exists at this URL."));
    }

    @ExceptionHandler(PhantomBusterException.class)
    public ResponseEntity<ApiResponse<Void>> handlePhantomBuster(PhantomBusterException e) {
        log.error("PhantomBuster request failed: {}", e.getMessage(), e);
        return reply(HttpStatus.BAD_GATEWAY, ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(ScrapingTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleTimeout(ScrapingTimeoutException e) {
        log.warn(e.getMessage());
        return reply(HttpStatus.GATEWAY_TIMEOUT, ApiResponse.error(e.getMessage()));
    }

    // Two searches running at the same moment can both try to insert the same new profile.
    // The unique LinkedIn URL rule stops the second one; asking the caller to retry is enough,
    // because the retry will find the saved row and update it.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(DataIntegrityViolationException e) {
        log.warn("Data conflict while saving alumni: {}", e.getMostSpecificCause().getMessage());
        return reply(HttpStatus.CONFLICT, ApiResponse.error(
                "Another search saved the same profiles at the same moment. Please try again."));
    }

    // CannotCreateTransactionException is what Spring raises when no database connection
    // can be opened at the start of a transaction; it is not a DataAccessException.
    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    public ResponseEntity<ApiResponse<Void>> handleDatabase(Exception e) {
        log.error("Database error", e);
        return reply(HttpStatus.SERVICE_UNAVAILABLE,
                ApiResponse.error("Could not save or read alumni data right now. Please try again later."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAnythingElse(Exception e) {
        log.error("Unexpected error", e);
        return reply(HttpStatus.INTERNAL_SERVER_ERROR,
                ApiResponse.error("Something went wrong on our side. Please try again later."));
    }

    private ResponseEntity<ApiResponse<Void>> reply(HttpStatus status, ApiResponse<Void> body) {
        return ResponseEntity.status(status).body(body);
    }
}
