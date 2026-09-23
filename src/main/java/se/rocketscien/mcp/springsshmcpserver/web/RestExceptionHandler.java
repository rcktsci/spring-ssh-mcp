package se.rocketscien.mcp.springsshmcpserver.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;

import se.rocketscien.mcp.springsshmcpserver.config.dto.ErrorResult;

// Scoped to the REST controllers package on purpose: a global catch-all would also swallow
// NoResourceFoundException and turn every unknown url into 500 instead of 404.
@RestControllerAdvice(basePackageClasses = RestExceptionHandler.class)
@Slf4j
public class RestExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResult> handleIllegalArgument(IllegalArgumentException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResult> handleResponseStatus(ResponseStatusException ex) {
        var reason = ex.getReason() != null ? ex.getReason() : "Not found";
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorResult(reason));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResult> handleMalformedRequest(Exception ex) {
        return body(HttpStatus.BAD_REQUEST, "Malformed request");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResult> handleUnexpected(Exception ex) {
        log.error("Unhandled exception in REST API", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
    }

    private ResponseEntity<ErrorResult> body(HttpStatus status, String error) {
        return ResponseEntity.status(status).body(new ErrorResult(error));
    }
}