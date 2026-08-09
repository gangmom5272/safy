package com.ojimasam.safy.publicdata.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.publicdata.domain.CollectionRun;
import com.ojimasam.safy.publicdata.domain.CollectionRunRepository;

public interface JpaCollectionRunRepository
		extends JpaRepository<CollectionRun, Long>, CollectionRunRepository {
}
