package com.ojimasam.safy.controlcenter.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import com.ojimasam.safy.controlcenter.domain.type.ControlCenterStatus;
import com.ojimasam.safy.controlcenter.domain.type.ControlCenterType;

class ControlCenterTest {
	@Test
	void createsActiveControlCenter() {
		ControlCenter center = ControlCenter.create(
				" CC-1168000000 ",
				" 강남구 재난안전상황실 ",
				ControlCenterType.DISTRICT,
				10,
				" 서울특별시 강남구 ",
				null
		);

		assertThat(center.getCode()).isEqualTo("CC-1168000000");
		assertThat(center.getName()).isEqualTo("강남구 재난안전상황실");
		assertThat(center.getStatus()).isEqualTo(ControlCenterStatus.ACTIVE);
		assertThat(center.getAddress()).isEqualTo("서울특별시 강남구");
	}

	@Test
	void rejectsInvalidCapacityLimit() {
		assertThatThrownBy(() -> ControlCenter.create(
				"CC-1168000000",
				"강남구 재난안전상황실",
				ControlCenterType.DISTRICT,
				0,
				null,
				null
		)).isInstanceOf(IllegalArgumentException.class);
	}
}
