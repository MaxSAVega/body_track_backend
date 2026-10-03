package com.rumi.body_track_backend.config;

import com.rumi.body_track_backend.dto.ErrorResponse;
import com.rumi.body_track_backend.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates every failure into the one {@link ErrorResponse} shape.
 *
 * <p>The single rule enforced here: <b>no exception message ever reaches the
 * client</b> unless it is an {@link ApiException}, whose message was authored to be
 * public. Everything else is logged server-side and replaced by a fixed string.
 * That removes the SQL/table/column/internal-path disclosure that
 * {@code ex.getMessage()} used to leak, along with the account-enumeration signal
 * carried by messages such as "usuario no encontrado".
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        // Auth and rate-limit failures are expected traffic, not defects: log at INFO.
        log.info("api_error code={} status={} path={} message={}",
                ex.getCode(), ex.getStatus().value(), request.getRequestURI(), ex.getMessage());

        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), null, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(),
                    fieldError.getDefaultMessage() == null
                            ? "valor inválido"
                            : fieldError.getDefaultMessage());
        }

        log.info("validation_failed path={} fields={}", request.getRequestURI(), fieldErrors.keySet());

        return build(HttpStatus.BAD_REQUEST, "validation_failed",
                "Los datos enviados no son válidos", fieldErrors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        log.info("constraint_violation path={}", request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "validation_failed",
                "Los datos enviados no son válidos", null, request);
    }

    /**
     * Replaces the framework's default 400, which echoes the parser's complaint
     * (and therefore the shape of the expected document) back to the caller.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.info("unreadable_body path={}", request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST, "malformed_request",
                "El cuerpo de la petición no es válido", null, request);
    }

    /**
     * The message of the underlying exception states the configured byte ceiling,
     * which is free reconnaissance, so it is replaced.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {

        log.info("upload_too_large path={}", request.getRequestURI());
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "file_too_large",
                "El archivo supera el tamaño permitido", null, request);
    }

    @ExceptionHandler({MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadBinding(Exception ex, HttpServletRequest request) {
        log.info("bad_binding path={} type={}", request.getRequestURI(), ex.getClass().getSimpleName());
        return build(HttpStatus.BAD_REQUEST, "bad_request",
                "La petición no es válida", null, request);
    }

    /**
     * An authenticated principal reached a rule it does not satisfy. Kept distinct
     * from 404 because nothing here can be used to probe for the existence of
     * another user's data.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        log.info("access_denied path={}", request.getRequestURI());
        return build(HttpStatus.FORBIDDEN, "forbidden",
                "No tienes permiso para esta operación", null, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest request) {

        log.info("no_resource path={}", request.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "not_found", "Recurso no encontrado", null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

        return build(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed",
                "Método no permitido", null, request);
    }

    /**
     * Hibernate's message for a constraint violation embeds the full statement,
     * the table name and every column. It is logged and never serialised.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.warn("data_integrity_violation path={} detail={}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "conflict",
                "No se pudo completar la operación", null, request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {

        log.info("missing_header path={} header={}", request.getRequestURI(), ex.getHeaderName());
        return build(HttpStatus.UNAUTHORIZED, "unauthorized",
                "Credenciales inválidas o sesión expirada", null, request);
    }

    /**
     * Catch-all. The client learns only that the request failed; the exception is
     * recorded in full for the operator.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {

        log.error("unhandled_exception path={}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                "Se produjo un error interno", null, request);
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String code,
            String message,
            Map<String, String> fieldErrors,
            HttpServletRequest request) {

        return ResponseEntity.status(status).body(ErrorResponse.builder()
                .code(code)
                .message(message)
                .fieldErrors(fieldErrors)
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build());
    }
}