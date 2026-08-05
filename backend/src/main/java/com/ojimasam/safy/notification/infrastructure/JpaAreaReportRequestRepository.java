package com.ojimasam.safy.notification.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.notification.domain.AreaReportRequest;

public interface JpaAreaReportRequestRepository extends JpaRepository<AreaReportRequest, Long> {
}
