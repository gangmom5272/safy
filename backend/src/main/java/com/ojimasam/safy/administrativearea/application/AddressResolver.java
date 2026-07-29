package com.ojimasam.safy.administrativearea.application;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 좌표를 사람이 읽는 상세 주소로 해석한다.
 * 행정구역 판별과 달리 서울 여부를 가리지 않으며, 주소가 없는 좌표는 빈 값을 돌려준다.
 */
public interface AddressResolver {
	Optional<String> resolve(BigDecimal latitude, BigDecimal longitude);
}
