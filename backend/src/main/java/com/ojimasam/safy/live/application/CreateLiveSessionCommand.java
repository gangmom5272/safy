package com.ojimasam.safy.live.application;

import java.math.BigDecimal;

public record CreateLiveSessionCommand(Long reporterId, String description,
		BigDecimal latitude, BigDecimal longitude, String address) {
}
