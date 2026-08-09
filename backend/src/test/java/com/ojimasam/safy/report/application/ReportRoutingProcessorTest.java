package com.ojimasam.safy.report.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.administrativearea.application.AddressResolutionException;
import com.ojimasam.safy.administrativearea.application.AddressResolver;
import com.ojimasam.safy.administrativearea.application.AdministrativeAreaResolutionException;
import com.ojimasam.safy.administrativearea.application.AdministrativeAreaResolver;
import com.ojimasam.safy.administrativearea.application.ResolvedAdministrativeArea;

class ReportRoutingProcessorTest {
	private static final ReportRoutingTarget TARGET =
			new ReportRoutingTarget(10L, new BigDecimal("37.5"), new BigDecimal("127.0"), false);
	private static final ReportRoutingTarget TARGET_WITH_ADDRESS =
			new ReportRoutingTarget(10L, new BigDecimal("37.5"), new BigDecimal("127.0"), true);

	private final AdministrativeAreaResolver resolver = mock(AdministrativeAreaResolver.class);
	private final AddressResolver addressResolver = mock(AddressResolver.class);
	private final ReportRoutingTransactionService transactionService = mock(ReportRoutingTransactionService.class);
	private final ReportRoutingProcessor processor =
			new ReportRoutingProcessor(resolver, addressResolver, transactionService);

	@Test
	void resolvesCoordinatesAndCompletesRouting() {
		ResolvedAdministrativeArea resolved = new ResolvedAdministrativeArea("1168000000", "강남구");
		when(transactionService.findPendingTarget(10L)).thenReturn(Optional.of(TARGET));
		when(resolver.resolve(TARGET.latitude(), TARGET.longitude())).thenReturn(Optional.of(resolved));

		processor.process(10L);

		verify(transactionService).completeAutomaticRouting(10L, resolved);
	}

	@Test
	void marksUnknownCoordinateForManualRouting() {
		when(transactionService.findPendingTarget(10L)).thenReturn(Optional.of(TARGET));
		when(resolver.resolve(TARGET.latitude(), TARGET.longitude())).thenReturn(Optional.empty());

		processor.process(10L);

		verify(transactionService).markUnresolvable(10L, "서울특별시 자치구로 판별할 수 없는 좌표입니다.");
	}

	@Test
	void schedulesRetryWhenResolverTemporarilyFails() {
		when(transactionService.findPendingTarget(10L)).thenReturn(Optional.of(TARGET));
		when(resolver.resolve(TARGET.latitude(), TARGET.longitude()))
				.thenThrow(new AdministrativeAreaResolutionException("temporary failure"));

		processor.process(10L);

		verify(transactionService).recordTransientFailure(10L, "temporary failure");
	}

	@Test
	void savesResolvedAddress() {
		when(transactionService.findPendingTarget(10L)).thenReturn(Optional.of(TARGET));
		when(addressResolver.resolve(TARGET.latitude(), TARGET.longitude()))
				.thenReturn(Optional.of("서울 강남구 역삼동 804"));
		when(resolver.resolve(TARGET.latitude(), TARGET.longitude()))
				.thenReturn(Optional.of(new ResolvedAdministrativeArea("1168000000", "강남구")));

		processor.process(10L);

		verify(transactionService).applyResolvedAddress(10L, "서울 강남구 역삼동 804");
	}

	@Test
	void savesAddressEvenWhenCoordinateIsOutsideSeoul() {
		when(transactionService.findPendingTarget(10L)).thenReturn(Optional.of(TARGET));
		when(addressResolver.resolve(TARGET.latitude(), TARGET.longitude()))
				.thenReturn(Optional.of("경기 성남시 분당구 정자동 178"));
		when(resolver.resolve(TARGET.latitude(), TARGET.longitude())).thenReturn(Optional.empty());

		processor.process(10L);

		verify(transactionService).applyResolvedAddress(10L, "경기 성남시 분당구 정자동 178");
		verify(transactionService).markUnresolvable(10L, "서울특별시 자치구로 판별할 수 없는 좌표입니다.");
	}

	/** 재시도는 process() 를 통째로 다시 돌리므로, 주소가 이미 있으면 카카오를 다시 부르지 않아야 한다. */
	@Test
	void skipsAddressLookupWhenReportAlreadyHasOne() {
		ResolvedAdministrativeArea resolved = new ResolvedAdministrativeArea("1168000000", "강남구");
		when(transactionService.findPendingTarget(10L)).thenReturn(Optional.of(TARGET_WITH_ADDRESS));
		when(resolver.resolve(TARGET.latitude(), TARGET.longitude())).thenReturn(Optional.of(resolved));

		processor.process(10L);

		verify(addressResolver, never()).resolve(any(), any());
		verify(transactionService, never()).applyResolvedAddress(any(), any());
		verify(transactionService).completeAutomaticRouting(10L, resolved);
	}

	@Test
	void routesNormallyWhenAddressLookupFails() {
		ResolvedAdministrativeArea resolved = new ResolvedAdministrativeArea("1168000000", "강남구");
		when(transactionService.findPendingTarget(10L)).thenReturn(Optional.of(TARGET));
		when(addressResolver.resolve(TARGET.latitude(), TARGET.longitude()))
				.thenThrow(new AddressResolutionException("temporary failure"));
		when(resolver.resolve(TARGET.latitude(), TARGET.longitude())).thenReturn(Optional.of(resolved));

		processor.process(10L);

		verify(transactionService, never()).applyResolvedAddress(any(), any());
		verify(transactionService, never()).recordTransientFailure(any(), any());
		verify(transactionService).completeAutomaticRouting(10L, resolved);
	}
}
