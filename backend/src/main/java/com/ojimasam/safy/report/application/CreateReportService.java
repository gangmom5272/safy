package com.ojimasam.safy.report.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.grid.application.GridCellRegistry;
import com.ojimasam.safy.place.application.PlaceLocator;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;
import com.ojimasam.safy.report.application.dto.CreateReportCommand;
import com.ojimasam.safy.report.application.dto.CreateReportResult;
import com.ojimasam.safy.report.application.event.ReportRoutingRequestedEvent;

@Service
public class CreateReportService {
	private final ReportRepository reportRepository;
	private final GridCellRegistry gridCellRegistry;
	private final PlaceLocator placeLocator;
	private final ApplicationEventPublisher eventPublisher;

	public CreateReportService(
			ReportRepository reportRepository,
			GridCellRegistry gridCellRegistry,
			PlaceLocator placeLocator,
			ApplicationEventPublisher eventPublisher
	) {
		this.reportRepository = reportRepository;
		this.gridCellRegistry = gridCellRegistry;
		this.placeLocator = placeLocator;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	public CreateReportResult create(CreateReportCommand command) {
		// 주소는 라우팅 단계에서 서버가 좌표로 확정해 채운다.
		Report report = Report.create(
				command.reporterId(), command.type(), command.category(), command.subcategory(), command.description(),
				command.latitude(), command.longitude(), null
		);
		// 셀 코드는 좌표에서 바로 나오지만, 격자 위험도가 이 셀을 참조하려면 행이 있어야 한다.
		gridCellRegistry.findOrCreate(command.latitude(), command.longitude());
		// 장소는 폴리곤 조회가 필요하다. 메모리에 올린 경계로 판정하므로
		// 외부 호출 없이 등록 흐름 안에서 끝난다.
		placeLocator.locate(command.latitude(), command.longitude())
				.ifPresent(report::applyResolvedPlace);

		Report saved = reportRepository.save(report);
		eventPublisher.publishEvent(new ReportRoutingRequestedEvent(saved.getId()));
		return CreateReportResult.from(saved);
	}
}
