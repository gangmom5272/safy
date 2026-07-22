package com.ojimasam.safy.report.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.media.domain.MediaAsset;

class ReportImageTest {
	@Test
	void connectsReportAndOriginalMediaAsset() {
		Report report = Report.createImage(1L, "현장 이미지", new BigDecimal("37.5"), new BigDecimal("127.0"), null);
		MediaAsset asset = MediaAsset.prepareOriginal(
				1L, "safy", "reports/1/image.jpg", "현장.jpg", "image/jpeg",
				Instant.now().plusSeconds(300)
		);

		ReportImage image = ReportImage.create(report, asset, 0);

		assertThat(image.getReport()).isSameAs(report);
		assertThat(image.getMediaAsset()).isSameAs(asset);
		assertThat(image.getDisplayOrder()).isZero();
	}
}
