package com.ojimasam.safy.administrativearea.application;

public record ResolvedAdministrativeArea(
		String code,
		String name
) {
	public ResolvedAdministrativeArea {
		if (code == null || code.isBlank()) throw new IllegalArgumentException("행정구역 코드는 필수입니다.");
		if (name == null || name.isBlank()) throw new IllegalArgumentException("행정구역 이름은 필수입니다.");
	}
}
