package com.ojimasam.safy.publicdata.domain;

import java.time.Instant;
import java.util.List;

public interface DisasterMessageRepository {
	boolean existsBySourceAndExternalId(String source, String externalId);

	DisasterMessage save(DisasterMessage disasterMessage);

	List<DisasterMessage> findAllBySentAtGreaterThanEqualOrderBySentAtDesc(Instant sentAt);
}
