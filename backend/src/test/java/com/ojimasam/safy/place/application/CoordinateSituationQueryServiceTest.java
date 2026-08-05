package com.ojimasam.safy.place.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.place.application.dto.CoordinateCongestionView;
import com.ojimasam.safy.place.domain.type.CongestionLevel;
import com.ojimasam.safy.publicdata.application.dto.CongestionObservationView;
import com.ojimasam.safy.report.application.RadiusRecentReportCounter;

@ExtendWith(MockitoExtension.class)
class CoordinateSituationQueryServiceTest {
	private static final Instant NOW = Instant.parse("2026-08-05T04:00:00Z");
	private static final BigDecimal LATITUDE = new BigDecimal("37.5013");
	private static final BigDecimal LONGITUDE = new BigDecimal("127.0396");

	@Mock CoordinateCongestionReader coordinateCongestionReader;
	@Mock RadiusRecentReportCounter radiusRecentReportCounter;

	/** 혼잡도와 최근 제보는 범위가 달라, 응답이 각각의 근거를 함께 담아야 각주로 밝힐 수 있다. */
	@Test
	void reportsBothTheCongestionMatchTypeAndTheReportRadius() {
		CongestionObservationView congestion = new CongestionObservationView(
				14L, "역삼역", CongestionLevel.SLIGHTLY_CROWDED, "사람이 몰리고 있어요.", 21000, 24000, NOW);
		when(coordinateCongestionReader.at(LATITUDE, LONGITUDE))
				.thenReturn(Optional.of(CoordinateCongestionView.nearest(congestion)));
		when(radiusRecentReportCounter.countWithin(eq(LATITUDE), eq(LONGITUDE), anyDouble())).thenReturn(2L);

		var result = service().status(LATITUDE, LONGITUDE);

		assertThat(result.congestion()).isSameAs(congestion);
		assertThat(result.congestionMatchType()).isEqualTo(CoordinateCongestionView.MATCH_NEAREST);
		assertThat(result.recentReportCount()).isEqualTo(2L);
		assertThat(result.reportRadiusMeters()).isEqualTo(1_000);
		assertThat(result.updatedAt()).isEqualTo(NOW);
	}

	/**
	 * 121장소가 근처에 없는 좌표가 실제로 많다. 그래도 최근 제보는 반경으로 셀 수 있으니
	 * 혼잡도만 비우고 화면은 열려야 한다.
	 */
	@Test
	void keepsReportCountWhenCongestionHasNoEvidence() {
		when(coordinateCongestionReader.at(LATITUDE, LONGITUDE)).thenReturn(Optional.empty());
		when(radiusRecentReportCounter.countWithin(eq(LATITUDE), eq(LONGITUDE), anyDouble())).thenReturn(3L);

		var result = service().status(LATITUDE, LONGITUDE);

		assertThat(result.congestion()).isNull();
		assertThat(result.congestionMatchType()).isNull();
		assertThat(result.recentReportCount()).isEqualTo(3L);
	}

	@Test
	void rejectsMissingCoordinate() {
		assertThatThrownBy(() -> service().status(null, LONGITUDE)).isInstanceOf(BusinessException.class);
		assertThatThrownBy(() -> service().status(LATITUDE, null)).isInstanceOf(BusinessException.class);
	}

	private CoordinateSituationQueryService service() {
		return new CoordinateSituationQueryService(
				coordinateCongestionReader, radiusRecentReportCounter, Clock.fixed(NOW, ZoneOffset.UTC));
	}
}
