package com.ojimasam.safy.analysis.infrastructure;

public class AiBlurException extends RuntimeException {
	private final AiBlurFailureCode failureCode;

	public AiBlurException(AiBlurFailureCode failureCode, String message) {
		super(message);
		this.failureCode = failureCode;
	}

	public AiBlurException(AiBlurFailureCode failureCode, String message, Throwable cause) {
		super(message, cause);
		this.failureCode = failureCode;
	}

	public AiBlurFailureCode getFailureCode() {
		return failureCode;
	}
}
