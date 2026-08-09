package com.ojimasam.safy.report.application;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ojimasam.safy.administrativearea.application.AddressResolutionException;
import com.ojimasam.safy.administrativearea.application.AddressResolver;
import com.ojimasam.safy.administrativearea.application.AdministrativeAreaResolutionException;
import com.ojimasam.safy.administrativearea.application.AdministrativeAreaResolver;
import com.ojimasam.safy.administrativearea.application.ResolvedAdministrativeArea;

@Service
public class ReportRoutingProcessor {
	private final AdministrativeAreaResolver administrativeAreaResolver;
	private final AddressResolver addressResolver;
	private final ReportRoutingTransactionService transactionService;

	public ReportRoutingProcessor(
			AdministrativeAreaResolver administrativeAreaResolver,
			AddressResolver addressResolver,
			ReportRoutingTransactionService transactionService
	) {
		this.administrativeAreaResolver = administrativeAreaResolver;
		this.addressResolver = addressResolver;
		this.transactionService = transactionService;
	}

	public void process(Long reportId) {
		Optional<ReportRoutingTarget> target = transactionService.findPendingTarget(reportId);
		if (target.isEmpty()) return;

		applyAddress(target.get());

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

	/**
	 * 주소는 관제사에게 보여줄 부가 정보라, 조회에 실패해도 라우팅은 그대로 진행한다.
	 * 실패를 전이 오류로 올리면 관할이 이미 정해진 제보의 배정이 주소 때문에 미뤄진다.
	 */
	private void applyAddress(ReportRoutingTarget target) {
		// 재시도는 process() 를 통째로 다시 돌린다. 좌표가 그대로인 이상 주소도 그대로다.
		if (target.hasAddress()) return;

		try {
			addressResolver.resolve(target.latitude(), target.longitude())
					.ifPresent(address -> transactionService.applyResolvedAddress(target.reportId(), address));
		} catch (AddressResolutionException exception) {
			// 주소 없이 진행한다.
		}
	}
}
