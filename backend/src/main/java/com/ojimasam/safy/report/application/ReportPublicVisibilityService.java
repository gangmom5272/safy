package com.ojimasam.safy.report.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.report.domain.Report;
import com.ojimasam.safy.report.domain.ReportRepository;

@Service
public class ReportPublicVisibilityService {
	private final ReportRepository reportRepository;
	private final OperatorReportAccessService operatorAccessService;

	public ReportPublicVisibilityService(ReportRepository reportRepository,
			OperatorReportAccessService operatorAccessService) {
		this.reportRepository = reportRepository;
		this.operatorAccessService = operatorAccessService;
	}

	/**
	 * 공개된 제보를 시민 피드에서 내린다. 업무 종료(=공개) 이후에 호출되므로 활성 배정이 남아 있지 않다.
	 * {@code requireAccess} 는 종결된 배정과 SUPER_ADMIN 을 모두 허용해 이 시점에 그대로 쓸 수 있다.
	 */
	@Transactional
	public void withhold(Long userId, Long reportId) {
		operatorAccessService.requireAccess(userId, reportId);
		Report report = reportRepository.findByIdForUpdate(reportId)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		report.withhold();
	}
}
