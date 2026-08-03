package com.ojimasam.safy.analysis.domain.risk;

/**
 * 격자 위험도 점수가 어떤 근거에서 나왔는지 나타낸다.
 *
 * <p>동점이면 {@link #REPORTED} 다. 제보가 더 강한 근거이기 때문이다. 제보가 있어도
 * 121곳 값이 더 높으면 {@link #PUBLIC_DATA} 이고, 제보가 있었다는 사실은
 * {@code evidenceCount} 와 {@code distinctReporterCount} 에 남는다.
 */
public enum GridObservationStatus {
	REPORTED,
	PUBLIC_DATA,
	NO_DATA
}
