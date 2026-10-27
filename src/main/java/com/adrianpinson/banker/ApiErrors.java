package com.adrianpinson.banker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler(BankException.class)
    ResponseEntity<ProblemDetail> bank(BankException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(exception.status()), exception.getMessage());
        problem.setProperty("code", exception.code());
        return ResponseEntity.status(exception.status()).body(problem);
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ProblemDetail> database(DataAccessException exception) {
        LOG.error("Database operation failed", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "The command could not be committed. Retry using the same Idempotency-Key.");
        problem.setProperty("code", "DATABASE_UNAVAILABLE");
        return ResponseEntity.status(503).body(problem);
    }
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Check required fields and allowed lengths or amounts.");
        problem.setProperty("code", "INVALID_REQUEST");
        return handleExceptionInternal(exception, problem, headers, status, request);
    }
}
