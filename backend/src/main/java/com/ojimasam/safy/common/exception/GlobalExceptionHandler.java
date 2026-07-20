package com.ojimasam.safy.common.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.ojimasam.safy.common.api.ApiErrorResponse;
import com.ojimasam.safy.common.api.FieldErrorDetail;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiErrorResponse> handleBusinessException(
			BusinessException exception,
			HttpServletRequest request
	) {
		return createResponse(exception.getErrorCode(), exception.getMessage(), List.of(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
			MethodArgumentNotValidException exception,
			HttpServletRequest request
	) {
		List<FieldErrorDetail> fieldErrors = exception.getBindingResult()
				.getFieldErrors()
				.stream()
				.map(this::toFieldErrorDetail)
				.toList();

		return createResponse(CommonErrorCode.INVALID_INPUT_VALUE, fieldErrors, request);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
			ConstraintViolationException exception,
			HttpServletRequest request
	) {
		List<FieldErrorDetail> fieldErrors = exception.getConstraintViolations()
				.stream()
				.map(violation -> new FieldErrorDetail(
						violation.getPropertyPath().toString(),
						violation.getMessage()
				))
				.toList();

		return createResponse(CommonErrorCode.INVALID_INPUT_VALUE, fieldErrors, request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleMalformedRequest(
			HttpMessageNotReadableException exception,
			HttpServletRequest request
	) {
		return createResponse(CommonErrorCode.MALFORMED_REQUEST, request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
			MethodArgumentTypeMismatchException exception,
			HttpServletRequest request
	) {
		FieldErrorDetail fieldError = new FieldErrorDetail(
				exception.getName(),
				CommonErrorCode.TYPE_MISMATCH.getMessage()
		);
		return createResponse(CommonErrorCode.TYPE_MISMATCH, List.of(fieldError), request);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingParameter(
			MissingServletRequestParameterException exception,
			HttpServletRequest request
	) {
		FieldErrorDetail fieldError = new FieldErrorDetail(
				exception.getParameterName(),
				CommonErrorCode.MISSING_PARAMETER.getMessage()
		);
		return createResponse(CommonErrorCode.MISSING_PARAMETER, List.of(fieldError), request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
			NoResourceFoundException exception,
			HttpServletRequest request
	) {
		return createResponse(CommonErrorCode.RESOURCE_NOT_FOUND, request);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
			HttpRequestMethodNotSupportedException exception,
			HttpServletRequest request
	) {
		return createResponse(CommonErrorCode.METHOD_NOT_ALLOWED, request);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
			HttpMediaTypeNotSupportedException exception,
			HttpServletRequest request
	) {
		return createResponse(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, request);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(
			MaxUploadSizeExceededException exception,
			HttpServletRequest request
	) {
		return createResponse(CommonErrorCode.FILE_SIZE_EXCEEDED, request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
			Exception exception,
			HttpServletRequest request
	) {
		log.error("Unhandled exception while processing {} {}", request.getMethod(), request.getRequestURI(), exception);
		return createResponse(CommonErrorCode.INTERNAL_SERVER_ERROR, request);
	}

	private FieldErrorDetail toFieldErrorDetail(FieldError fieldError) {
		return new FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage());
	}

	private ResponseEntity<ApiErrorResponse> createResponse(
			ErrorCode errorCode,
			HttpServletRequest request
	) {
		return createResponse(errorCode, errorCode.getMessage(), List.of(), request);
	}

	private ResponseEntity<ApiErrorResponse> createResponse(
			ErrorCode errorCode,
			List<FieldErrorDetail> fieldErrors,
			HttpServletRequest request
	) {
		return createResponse(errorCode, errorCode.getMessage(), fieldErrors, request);
	}

	private ResponseEntity<ApiErrorResponse> createResponse(
			ErrorCode errorCode,
			String message,
			List<FieldErrorDetail> fieldErrors,
			HttpServletRequest request
	) {
		ApiErrorResponse body = ApiErrorResponse.of(
				errorCode.getCode(),
				message,
				fieldErrors,
				request.getRequestURI()
		);
		return ResponseEntity.status(errorCode.getStatus()).body(body);
	}
}
