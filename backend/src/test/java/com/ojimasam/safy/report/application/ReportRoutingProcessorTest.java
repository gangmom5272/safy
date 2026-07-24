package com.ojimasam.safy.report.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ojimasam.safy.administrativearea.application.AdministrativeAreaResolutionException;
import com.ojimasam.safy.administrativearea.application.AdministrativeAreaResolver;
import com.ojimasam.safy.administrativearea.application.ResolvedAdministrativeArea;

class ReportRoutingProcessorTest {
	@Test
	void resolvesCoordinatesAndCompletesRouting() {
		AdministrativeAreaResolver resolver = mock(AdministrativeAreaResolver.class);
		ReportRoutingTransactionService transactionService = mock(ReportRoutingTransactionService.class);
		ReportRoutingTarget target = new ReportRoutingTarget(10L, new BigDecimal("37.5"), new BigDecimal("127.0"));
		ResolvedAdministrativeArea resolved = new ResolvedAdministrativeArea("1168000000", "강남구");
		when(transactionService.findPendingTarget(10L)).thenReturn(Optional.of(target));
		when(resolver.resolve(target.latitude(), target.longitude())).thenReturn(Optional.of(resolved));
		ReportRoutingProcessor processor = new ReportRoutingProcessor(resolver, transactionService);

		processor.process(10L);

		verify(transactionService).completeAutomaticRouting(10L, resolved);
	}

	@Test
	void marksUnknownCoordinateForManualRouting() {
		AdministrativeAreaResolver resolver = mock(AdministrativeAreaResolver.class);
		ReportRoutingTransactionService transactionService = mock(ReportRoutingTransactionService.class);
		ReportRoutingTarget target = new ReportRoutingTarget(10L, new BigDecimal("37.5"), new BigDecimal("127.0"));
		when(transactionService.findPendingTarget(10L)).thenReturn(Optional.of(target));
		when(resolver.resolve(target.latitude(), target.longitude())).thenReturn(Optional.empty());
		ReportRoutingProcessor processor = new ReportRoutingProcessor(resolver, transactionService);

		processor.process(10L);

		verify(transactionService).markUnresolvable(10L, "서울특별시 자치구로 판별할 수 없는 좌표입니다.");
	}

	@Test
	void schedulesRetryWhenResolverTemporarilyFails() {
		AdministrativeAreaResolver resolver = mock(AdministrativeAreaResolver.class);
		ReportRoutingTransactionService transactionService = mock(ReportRoutingTransactionService.class);
		ReportRoutingTarget target = new ReportRoutingTarget(10L, new BigDecimal("37.5"), new BigDecimal("127.0"));
		when(transactionService.findPendingTarget(10L)).thenReturn(Optional.of(target));
		when(resolver.resolve(target.latitude(), target.longitude()))
				.thenThrow(new AdministrativeAreaResolutionException("temporary failure"));
		ReportRoutingProcessor processor = new ReportRoutingProcessor(resolver, transactionService);

		processor.process(10L);

		verify(transactionService).recordTransientFailure(10L, "temporary failure");
	}
}
