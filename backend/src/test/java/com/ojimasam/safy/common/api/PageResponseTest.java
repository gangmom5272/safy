package com.ojimasam.safy.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {
	@Test
	void convertsSpringDataPage() {
		PageImpl<String> page = new PageImpl<>(
				List.of("report-1", "report-2"),
				PageRequest.of(1, 2),
				5
		);

		PageResponse<String> response = PageResponse.from(page);

		assertThat(response.content()).containsExactly("report-1", "report-2");
		assertThat(response.page()).isEqualTo(1);
		assertThat(response.size()).isEqualTo(2);
		assertThat(response.totalElements()).isEqualTo(5);
		assertThat(response.totalPages()).isEqualTo(3);
		assertThat(response.first()).isFalse();
		assertThat(response.last()).isFalse();
	}
}
