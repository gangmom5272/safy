package com.ojimasam.safy.notification.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisableWebPushRequest(@NotBlank @Size(max = 2048) String endpoint) {
}
