package com.ojimasam.safy.controlcenter.application;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.admin.domain.AdminCenterMembershipRepository;
import com.ojimasam.safy.admin.domain.MembershipStatus;
import com.ojimasam.safy.administrativearea.domain.AdministrativeArea;
import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaRepository;
import com.ojimasam.safy.administrativearea.domain.AdministrativeAreaStatus;
import com.ojimasam.safy.common.api.PageResponse;
import com.ojimasam.safy.common.exception.BusinessException;
import com.ojimasam.safy.common.exception.CommonErrorCode;
import com.ojimasam.safy.controlcenter.domain.ControlCenter;
import com.ojimasam.safy.controlcenter.domain.ControlCenterArea;
import com.ojimasam.safy.controlcenter.domain.ControlCenterAreaRepository;
import com.ojimasam.safy.controlcenter.domain.ControlCenterErrorCode;
import com.ojimasam.safy.controlcenter.domain.ControlCenterRepository;
import com.ojimasam.safy.controlcenter.domain.ControlCenterStatus;
import com.ojimasam.safy.controlcenter.domain.CoverageStatus;

@Service
public class ControlCenterService {
	private final ControlCenterRepository controlCenterRepository;
	private final ControlCenterAreaRepository controlCenterAreaRepository;
	private final AdministrativeAreaRepository administrativeAreaRepository;
	private final AdminCenterMembershipRepository membershipRepository;

	public ControlCenterService(
			ControlCenterRepository controlCenterRepository,
			ControlCenterAreaRepository controlCenterAreaRepository,
			AdministrativeAreaRepository administrativeAreaRepository,
			AdminCenterMembershipRepository membershipRepository
	) {
		this.controlCenterRepository = controlCenterRepository;
		this.controlCenterAreaRepository = controlCenterAreaRepository;
		this.administrativeAreaRepository = administrativeAreaRepository;
		this.membershipRepository = membershipRepository;
	}

	@Transactional
	public ControlCenterResponse create(CreateControlCenterRequest request) {
		String code = request.code().trim();
		if (controlCenterRepository.existsByCode(code)) {
			throw new BusinessException(ControlCenterErrorCode.CONTROL_CENTER_CODE_DUPLICATED);
		}
		ControlCenter center = ControlCenter.create(
				code,
				request.name(),
				request.centerType(),
				request.capacityLimit(),
				request.address(),
				request.phoneNumber()
		);
		return ControlCenterResponse.from(controlCenterRepository.save(center));
	}

	@Transactional(readOnly = true)
	public PageResponse<ControlCenterResponse> list(ControlCenterStatus status, Pageable pageable) {
		Page<ControlCenter> centers = status == null
				? controlCenterRepository.findAll(pageable)
				: controlCenterRepository.findAllByStatus(status, pageable);
		return PageResponse.from(centers.map(ControlCenterResponse::from));
	}

	@Transactional(readOnly = true)
	public ControlCenterResponse get(Long centerId) {
		return ControlCenterResponse.from(center(centerId));
	}

	@Transactional
	public ControlCenterResponse update(Long centerId, UpdateControlCenterRequest request) {
		ControlCenter center = center(centerId);
		if (request.status() == ControlCenterStatus.INACTIVE) {
			ensureNoActiveOperator(centerId);
			deactivateAreas(centerId, Instant.now());
		}
		center.update(
				request.name(),
				request.centerType(),
				request.status(),
				request.capacityLimit(),
				request.address(),
				request.phoneNumber()
		);
		return ControlCenterResponse.from(center);
	}

	@Transactional
	public void deactivate(Long centerId) {
		ControlCenter center = center(centerId);
		ensureNoActiveOperator(centerId);
		deactivateAreas(centerId, Instant.now());
		center.deactivate();
	}

	@Transactional(readOnly = true)
	public List<ControlCenterAreaResponse> listAreas(Long centerId) {
		center(centerId);
		return controlCenterAreaRepository
				.findAllByControlCenter_IdOrderByPriorityAscAdministrativeArea_NameAsc(centerId)
				.stream()
				.map(ControlCenterAreaResponse::from)
				.toList();
	}

	@Transactional
	public ControlCenterAreaResponse assignArea(Long centerId, AssignControlCenterAreaRequest request) {
		ControlCenter center = activeCenter(centerId);
		AdministrativeArea area = administrativeAreaRepository
				.findByIdAndStatus(request.administrativeAreaId(), AdministrativeAreaStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		if (controlCenterAreaRepository.existsByControlCenter_IdAndAdministrativeArea_IdAndCoverageRole(
				centerId,
				request.administrativeAreaId(),
				request.coverageRole()
		)) {
			throw new BusinessException(ControlCenterErrorCode.CONTROL_CENTER_AREA_DUPLICATED);
		}
		Instant effectiveFrom = request.effectiveFrom() == null ? Instant.now() : request.effectiveFrom();
		ControlCenterArea coverage = ControlCenterArea.assign(
				center,
				area,
				request.coverageRole(),
				request.priority(),
				effectiveFrom,
				request.effectiveTo()
		);
		return ControlCenterAreaResponse.from(controlCenterAreaRepository.save(coverage));
	}

	@Transactional
	public ControlCenterAreaResponse updateArea(
			Long centerId,
			Long coverageId,
			UpdateControlCenterAreaRequest request
	) {
		ControlCenterArea coverage = coverage(centerId, coverageId);
		if (coverage.getCoverageRole() != request.coverageRole()
				&& controlCenterAreaRepository.existsByControlCenter_IdAndAdministrativeArea_IdAndCoverageRole(
						centerId,
						coverage.getAdministrativeArea().getId(),
						request.coverageRole()
				)) {
			throw new BusinessException(ControlCenterErrorCode.CONTROL_CENTER_AREA_DUPLICATED);
		}
		coverage.update(
				request.coverageRole(),
				request.priority(),
				request.status(),
				request.effectiveFrom(),
				request.effectiveTo()
		);
		return ControlCenterAreaResponse.from(coverage);
	}

	@Transactional
	public void deactivateArea(Long centerId, Long coverageId) {
		coverage(centerId, coverageId).deactivate(Instant.now());
	}

	private ControlCenter center(Long centerId) {
		return controlCenterRepository.findById(centerId)
				.orElseThrow(() -> new BusinessException(ControlCenterErrorCode.CONTROL_CENTER_NOT_FOUND));
	}

	private ControlCenter activeCenter(Long centerId) {
		return controlCenterRepository.findByIdAndStatus(centerId, ControlCenterStatus.ACTIVE)
				.orElseThrow(() -> new BusinessException(ControlCenterErrorCode.CONTROL_CENTER_NOT_FOUND));
	}

	private ControlCenterArea coverage(Long centerId, Long coverageId) {
		return controlCenterAreaRepository.findByIdAndControlCenter_Id(coverageId, centerId)
				.orElseThrow(() -> new BusinessException(ControlCenterErrorCode.CONTROL_CENTER_AREA_NOT_FOUND));
	}

	private void ensureNoActiveOperator(Long centerId) {
		if (membershipRepository.existsByControlCenter_IdAndStatus(centerId, MembershipStatus.ACTIVE)) {
			throw new BusinessException(ControlCenterErrorCode.CONTROL_CENTER_HAS_ACTIVE_OPERATOR);
		}
	}

	private void deactivateAreas(Long centerId, Instant endedAt) {
		controlCenterAreaRepository
				.findAllByControlCenter_IdOrderByPriorityAscAdministrativeArea_NameAsc(centerId)
				.stream()
				.filter(coverage -> coverage.getStatus() == CoverageStatus.ACTIVE)
				.forEach(coverage -> coverage.deactivate(endedAt));
	}
}
