package com.sprintjudge.exception;

import com.sprintjudge.domain.dto.ErrorMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
@SuppressWarnings("all")
public class GlobalExceptionHandler {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** Only these fixed reasons are safe to echo; everything else is generic. */
	private static final java.util.Set<String> SAFE_REASONS = java.util.Set.of("rate_limited", "public_runner_disabled",
			"no practice set yet", "not_implemented", "student_identity_required", "student_identity_mismatch",
			"student_not_in_roster", "studentId is required", "subject is required", "sessionId is required",
			"question id is required", "unknown question id", "unknown session", "teach-next service unavailable",
			"profile_token_required", "player_uuid already linked", "profile_not_found", "invalid_profile_token",
			"invalid_rejoin_token", "nickname is required", "medianSeconds is required", "questionMedian is required",
			"accuracy is required", "medianSeconds must be >= 0", "questionMedian must be >= 0",
			"accuracy must be between 0 and 1", "Quiz id already exists", "Quiz not found", "Title must not be blank",
			"Title exceeds 200 characters", "Description exceeds 4000 characters", "Question not found",
			"quiz is still referenced", "quizId is required", "Missing or invalid 'json' field",
			"Import too large (max 2MB)");
	/** Stable kebab/snake-case codes are safe; arbitrary exception text is not. */
	private static final java.util.regex.Pattern SAFE_CODE = java.util.regex.Pattern
			.compile("[a-z0-9]+(?:[_-][a-z0-9]+)+");

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorMessage> handleBadRequest(IllegalArgumentException e) {
		log.warn("Rejected bad request", e);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage("ERROR", "Bad request"));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorMessage> handleValidation(MethodArgumentNotValidException e) {
		List<org.springframework.validation.FieldError> errors = e.getBindingResult().getFieldErrors();
		StringBuilder detailsBuilder = new StringBuilder();
		for (int i = 0; i < errors.size(); i++) {
			org.springframework.validation.FieldError err = errors.get(i);
			if (i > 0)
				detailsBuilder.append("; ");
			detailsBuilder.append(err.getField()).append(": ").append(err.getDefaultMessage());
		}
		String details = detailsBuilder.toString();
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorMessage("ERROR", "Validation failed: " + details));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorMessage> handleUnreadable(HttpMessageNotReadableException e) {
		org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).warn("Unreadable request body", e);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorMessage("ERROR", "Malformed JSON body"));
	}

	@ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
	public ResponseEntity<ErrorMessage> handleStatus(org.springframework.web.server.ResponseStatusException e) {
		String reason = e.getReason();
		String safe = reason != null && (SAFE_REASONS.contains(reason) || SAFE_CODE.matcher(reason).matches())
				? reason
				: "Request rejected";
		return ResponseEntity.status(e.getStatusCode()).body(new ErrorMessage("ERROR", safe));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ErrorMessage> handleConflict(IllegalStateException e) {
		log.warn("Conflict", e);
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorMessage("ERROR", "Conflict"));
	}

	@ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
	public ResponseEntity<ErrorMessage> handleNoResource(
			org.springframework.web.servlet.resource.NoResourceFoundException e) {
		// Browsers probe /favicon.ico and similar paths constantly; a missing
		// static asset is a 404, never a logged stack trace.
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorMessage("ERROR", "Not found"));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorMessage> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
		// An unsupported verb (e.g. DELETE on a read-only endpoint) is a client
		// error; the catch-all below would otherwise turn it into a 500.
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
				.body(new ErrorMessage("ERROR", "Method not allowed"));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorMessage> handleGeneric(Exception e) {
		// Silent 500s are unacceptable: always record the stack before answering.
		log.error("Unhandled server error", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorMessage("ERROR", "Internal error"));
	}
}
