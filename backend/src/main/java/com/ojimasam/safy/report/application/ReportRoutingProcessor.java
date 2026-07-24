package com.ojimasam.safy.report.application;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ojimasam.safy.administrativearea.application.AdministrativeAreaResolutionException;
import com.ojimasam.safy.administrativearea.application.AdministrativeAreaResolver;
import com.ojimasam.safy.administrativearea.application.ResolvedAdministrativeArea;

@Service
public class ReportRoutingProcessor {
	private final AdministrativeAreaResolver administrativeAreaResolver;
	private final ReportRoutingTransactionService transactionService;

	public ReportRoutingProcessor(
			AdministrativeAreaResolver administrativeAreaResolver,
			ReportRoutingTransactionService transactionService
	) {
		this.administrativeAreaResolver = administrativeAreaResolver;
		this.transactionService = transactionService;
	}

	public void process(Long reportId) {
		Optional<ReportRoutingTarget> target = transactionService.findPendingTarget(reportId);
		if (target.isEmpty()) return;
		try {
			Optional<ResolvedAdministrativeArea> resolved = administrativeAreaResolver.resolve(
					target.get().latitude(),
					target.get().longitude()
			);
			if (resolved.isEmpty()) {
				transactionService.markUnresolvable(reportId, "서울특별시 자치구로 판별할 수 없는 좌표입니다.");
				return;
			}
			transactionService.completeAutomaticRouting(reportId, resolved.get());
		} catch (AdministrativeAreaResolutionException exception) {
			transactionService.recordTransientFailure(reportId, exception.getMessage());
		}
	}
}
