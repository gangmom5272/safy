package com.ojimasam.safy.report.domain;

import com.ojimasam.safy.common.persistence.BaseEntity;
import com.ojimasam.safy.media.domain.MediaAsset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "report_images",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_report_images_media_asset", columnNames = "media_asset_id"),
				@UniqueConstraint(name = "uk_report_images_order", columnNames = {"report_id", "display_order"})
		}
)
public class ReportImage extends BaseEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id", nullable = false)
	private Report report;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "media_asset_id", nullable = false)
	private MediaAsset mediaAsset;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	protected ReportImage() {
	}

	private ReportImage(Report report, MediaAsset mediaAsset, int displayOrder) {
		this.report = report;
		this.mediaAsset = mediaAsset;
		this.displayOrder = displayOrder;
	}

	public static ReportImage create(Report report, MediaAsset mediaAsset, int displayOrder) {
		if (report == null) throw new IllegalArgumentException("제보는 필수입니다.");
		if (mediaAsset == null) throw new IllegalArgumentException("미디어는 필수입니다.");
		if (displayOrder < 0) throw new IllegalArgumentException("이미지 순서는 0 이상이어야 합니다.");
		return new ReportImage(report, mediaAsset, displayOrder);
	}

	public Long getId() { return id; }
	public Report getReport() { return report; }
	public MediaAsset getMediaAsset() { return mediaAsset; }
	public int getDisplayOrder() { return displayOrder; }
}
