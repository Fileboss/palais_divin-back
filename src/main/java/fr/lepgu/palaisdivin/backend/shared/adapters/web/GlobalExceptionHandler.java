package fr.lepgu.palaisdivin.backend.shared.adapters.web;

import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import io.micrometer.tracing.Tracer;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  static final URI PROBLEM_BASE = URI.create("https://palaisdivin.lepgu.fr/problems/");

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final ObjectProvider<Tracer> tracer;

  public GlobalExceptionHandler(ObjectProvider<Tracer> tracer) {
    this.tracer = tracer;
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail pd = ProblemDetail.forStatus(status);
    pd.setType(PROBLEM_BASE.resolve("validation"));
    pd.setTitle("Validation failed");
    pd.setProperty(
        "errors",
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                fe ->
                    Map.of(
                        "field", fe.getField(),
                        "code", String.valueOf(fe.getCode()),
                        "message", String.valueOf(fe.getDefaultMessage())))
            .toList());
    addTraceId(pd);
    return new ResponseEntity<>(pd, headers, status);
  }

  @Override
  protected ResponseEntity<Object> handleNoResourceFoundException(
      NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail pd = ProblemDetail.forStatus(status);
    pd.setType(PROBLEM_BASE.resolve("not-found"));
    pd.setTitle("Resource not found");
    addTraceId(pd);
    return new ResponseEntity<>(pd, headers, status);
  }

  @ExceptionHandler(RestaurantNotFoundException.class)
  ResponseEntity<ProblemDetail> handleRestaurantNotFound(RestaurantNotFoundException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    pd.setType(PROBLEM_BASE.resolve("not-found"));
    pd.setTitle("Resource not found");
    addTraceId(pd);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    pd.setType(PROBLEM_BASE.resolve("bad-request"));
    pd.setTitle("Bad request");
    addTraceId(pd);
    return ResponseEntity.badRequest().body(pd);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleAny(Exception ex) {
    log.error("Unhandled exception reaching the global advice", ex);
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    pd.setType(PROBLEM_BASE.resolve("internal"));
    pd.setTitle("Internal server error");
    pd.setDetail("An unexpected error occurred.");
    addTraceId(pd);
    return ResponseEntity.internalServerError().body(pd);
  }

  private void addTraceId(ProblemDetail pd) {
    Tracer t = tracer.getIfAvailable();
    if (t != null) {
      var span = t.currentSpan();
      if (span != null) {
        pd.setProperty("traceId", span.context().traceId());
      }
    }
  }
}
