package com.ojimasam.safy.administrativearea.application;

import java.math.BigDecimal;
import java.util.Optional;

public interface AdministrativeAreaResolver {
	Optional<ResolvedAdministrativeArea> resolve(BigDecimal latitude, BigDecimal longitude);
}
