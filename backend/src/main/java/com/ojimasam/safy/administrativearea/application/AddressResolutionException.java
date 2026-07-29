package com.ojimasam.safy.administrativearea.application;

public class AddressResolutionException extends RuntimeException {
	public AddressResolutionException(String message) {
		super(message);
	}

	public AddressResolutionException(String message, Throwable cause) {
		super(message, cause);
	}
}
