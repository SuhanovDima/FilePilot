package io.github.filepilot.web;

import io.github.filepilot.config.PilotException;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(PilotException.class) ResponseEntity<ProblemDetail> error(PilotException e) {
        HttpStatus status = e.code().equals("NOT_FOUND") ? HttpStatus.NOT_FOUND : e.code().contains("UNAUTHORIZED") ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT;
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, e.code()); p.setTitle("FilePilot refused the operation"); p.setProperty("code", e.code());
        return ResponseEntity.status(status).body(p);
    }
    @ExceptionHandler({IOException.class, IllegalArgumentException.class, org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class}) ResponseEntity<ProblemDetail> io(Exception e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid input or unavailable filesystem entry"); p.setProperty("code", "INVALID_REQUEST");
        return ResponseEntity.badRequest().body(p);
    }
}
