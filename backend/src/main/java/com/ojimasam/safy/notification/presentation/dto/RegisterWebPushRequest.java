package com.ojimasam.safy.notification.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterWebPushRequest(
		@NotBlank @Size(max = 2048) String endpoint,
		@NotNull @Valid Keys keys,
		@Size(max = 512) String userAgent
) {
	public record Keys(
			@NotBlank @Size(max = 255) String p256dh,
			@NotBlank @Size(max = 255) String auth
	) {
	}
}
