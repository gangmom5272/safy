package com.ojimasam.safy.analysis.infrastructure;

public class AiAnalysisException extends RuntimeException {
	private final AiAnalysisFailureCode failureCode;

	public AiAnalysisException(AiAnalysisFailureCode failureCode, String message) {
		super(message);
		this.failureCode = failureCode;
	}

	public AiAnalysisException(AiAnalysisFailureCode failureCode, String message, Throwable cause) {
		super(message, cause);
		this.failureCode = failureCode;
	}

	public AiAnalysisFailureCode getFailureCode() {
		return failureCode;
	}
}
