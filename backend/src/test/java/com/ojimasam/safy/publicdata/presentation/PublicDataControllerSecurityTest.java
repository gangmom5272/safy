package com.ojimasam.safy.publicdata.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 공공데이터 조회는 로그인 전 지도에서 호출하므로 인증 없이 열려 있어야 한다.
 * SecurityConfig 의 permitAll 규칙이 사라지면 지도가 조용히 비게 되므로 여기서 고정한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicDataControllerSecurityTest {
	@Autowired
	private MockMvc mockMvc;

	@Test
	void congestionIsReadableWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/public-data/congestion"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	@Test
	void eventsAreReadableWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/public-data/events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	/** 공개 범위가 공공데이터 밖으로 넓어지지 않았는지 함께 확인한다. */
	@Test
	void otherApisStillRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/administrative-areas"))
				.andExpect(status().isUnauthorized());
	}
}
