package com.ojimasam.safy.publicdata.domain;

import java.util.Optional;

public interface CollectionRunRepository {
	Optional<CollectionRun> findByExternalRunId(String externalRunId);

	Optional<CollectionRun> findById(Long id);

	CollectionRun save(CollectionRun collectionRun);
}
