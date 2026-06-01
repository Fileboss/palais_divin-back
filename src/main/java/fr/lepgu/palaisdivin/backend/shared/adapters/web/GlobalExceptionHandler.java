package fr.lepgu.palaisdivin.backend.shared.adapters.web;

import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.UnresolvableAddressException;
import fr.lepgu.palaisdivin.backend.user.domain.InvitationNotFoundException;
import fr.lepgu.palaisdivin.backend.user.domain.InvitationNotUsableException;
import fr.lepgu.palaisdivin.backend.user.domain.KeycloakOperationException;
import fr.lepgu.palaisdivin.backend.user.domain.SelfConnectionException;
import fr.lepgu.palaisdivin.backend.user.domain.UserNotFoundException;
import io.micrometer.tracing.Tracer;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
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

  @ExceptionHandler(UserNotFoundException.class)
  ResponseEntity<ProblemDetail> handleUserNotFound(UserNotFoundException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    pd.setType(PROBLEM_BASE.resolve("not-found"));
    pd.setTitle("Resource not found");
    addTraceId(pd);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
  }

  @ExceptionHandler(SelfConnectionException.class)
  ResponseEntity<ProblemDetail> handleSelfConnection(SelfConnectionException ex) {
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    pd.setType(PROBLEM_BASE.resolve("self-connection"));
    pd.setTitle("Self-connection not allowed");
    addTraceId(pd);
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(pd);
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

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    pd.setType(PROBLEM_BASE.resolve("validation"));
    pd.setTitle("Validation failed");
    pd.setProperty(
        "errors",
        ex.getConstraintViolations().stream()
            .map(
                v ->
                    Map.of(
                        "field", v.getPropertyPath().toString(),
                        "message", String.valueOf(v.getMessage())))
            .toList());
    addTraceId(pd);
    return ResponseEntity.badRequest().body(pd);
  }

  @ExceptionHandler(UnresolvableAddressException.class)
  ResponseEntity<ProblemDetail> handleUnresolvableAddress(UnresolvableAddressException ex) {
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    pd.setType(PROBLEM_BASE.resolve("unresolvable-address"));
    pd.setTitle("Address could not be resolved");
    pd.setProperty("address", ex.address());
    addTraceId(pd);
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(pd);
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException ex) {
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
    pd.setType(PROBLEM_BASE.resolve("unauthorized"));
    pd.setTitle("Unauthorized");
    pd.setDetail("Authentication is required to access this resource.");
    addTraceId(pd);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(pd);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    pd.setType(PROBLEM_BASE.resolve("forbidden"));
    pd.setTitle("Forbidden");
    pd.setDetail("You do not have permission to access this resource.");
    addTraceId(pd);
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(pd);
  }

  @ExceptionHandler(InvalidCursorException.class)
  ResponseEntity<ProblemDetail> handleInvalidCursor(InvalidCursorException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    pd.setType(PROBLEM_BASE.resolve("invalid-cursor"));
    pd.setTitle("Invalid cursor");
    addTraceId(pd);
    return ResponseEntity.badRequest().body(pd);
  }

  @ExceptionHandler(InvitationNotFoundException.class)
  ResponseEntity<ProblemDetail> handleInvitationNotFound(InvitationNotFoundException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    pd.setType(PROBLEM_BASE.resolve("not-found"));
    pd.setTitle("Resource not found");
    addTraceId(pd);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pd);
  }

  @ExceptionHandler(InvitationNotUsableException.class)
  ResponseEntity<ProblemDetail> handleInvitationNotUsable(InvitationNotUsableException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
    pd.setType(PROBLEM_BASE.resolve("invitation-not-usable"));
    pd.setTitle("Invitation not usable");
    pd.setProperty("reason", ex.reason().name());
    addTraceId(pd);
    return ResponseEntity.status(HttpStatus.GONE).body(pd);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
    log.warn("Data integrity violation surfaced as 409 conflict", ex);
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "The request conflicts with the current state of the resource.");
    pd.setType(PROBLEM_BASE.resolve("conflict"));
    pd.setTitle("Conflict");
    addTraceId(pd);
    return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
  }

  @ExceptionHandler(KeycloakOperationException.class)
  ResponseEntity<ProblemDetail> handleKeycloakOperation(KeycloakOperationException ex) {
    Integer status = ex.statusCode();
    if (status != null && status == HttpStatus.CONFLICT.value()) {
      ProblemDetail pd =
          ProblemDetail.forStatusAndDetail(
              HttpStatus.CONFLICT, "An account with this email already exists.");
      pd.setType(PROBLEM_BASE.resolve("conflict"));
      pd.setTitle("Conflict");
      addTraceId(pd);
      return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
    }
    log.error("Keycloak operation failure", ex);
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
    pd.setType(PROBLEM_BASE.resolve("upstream-failure"));
    pd.setTitle("Upstream failure");
    pd.setDetail("Identity provider request failed.");
    addTraceId(pd);
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(pd);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    pd.setType(PROBLEM_BASE.resolve("validation"));
    pd.setTitle("Validation failed");
    addTraceId(pd);
    return new ResponseEntity<>(pd, headers, HttpStatus.BAD_REQUEST);
  }

  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    pd.setType(PROBLEM_BASE.resolve("bad-request"));
    pd.setTitle("Bad request");
    addTraceId(pd);
    return new ResponseEntity<>(pd, headers, HttpStatus.BAD_REQUEST);
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
