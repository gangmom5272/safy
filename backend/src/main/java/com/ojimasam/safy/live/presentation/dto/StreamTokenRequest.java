package com.ojimasam.safy.live.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record StreamTokenRequest(@NotNull ParticipantType participantType) {
	public enum ParticipantType {
		REPORTER
	}
}
