package com.ojimasam.safy.publicdata.infrastructure;

import java.util.List;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ojimasam.safy.publicdata.domain.CityCongestionObservation;
import com.ojimasam.safy.publicdata.domain.CityCongestionObservationRepository;

public interface JpaCityCongestionObservationRepository
		extends JpaRepository<CityCongestionObservation, Long>, CityCongestionObservationRepository {

	/**
	 * 지역별 최신 1건만 뽑는다. 관측 행이 수백만 건이어도 지역은 121개뿐이고
	 * (place_id, observed_at DESC) 인덱스로 지역별 MAX 를 바로 찾으므로 전체 스캔이 일어나지 않는다.
	 * (place_id, observed_at) 유일 제약이 있어 동일 시각 중복 행은 생기지 않는다.
	 */
	@Override
	@Query("""
			select observation
			from CityCongestionObservation observation
			join fetch observation.place place
			where place.active = true
			  and observation.observedAt = (
				select max(latest.observedAt)
				from CityCongestionObservation latest
				where latest.place = place
			  )
			order by place.name asc
			""")
	List<CityCongestionObservation> findLatestPerPlace();

	/**
	 * 엔티티를 메모리에 올리지 않고 한 번의 DML 로 지운다.
	 * 수십만 행을 대상으로 하므로 엔티티 단위 삭제를 쓰면 안 된다.
	 */
	@Override
	@Modifying
	@Query("delete from CityCongestionObservation observation where observation.observedAt < :threshold")
	int deleteObservedBefore(@Param("threshold") Instant threshold);
}
