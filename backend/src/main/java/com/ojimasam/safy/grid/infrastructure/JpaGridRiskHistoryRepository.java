package com.ojimasam.safy.grid.infrastructure;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ojimasam.safy.grid.domain.GridRiskHistory;
import com.ojimasam.safy.grid.domain.GridRiskHistoryRepository;

public interface JpaGridRiskHistoryRepository
		extends JpaRepository<GridRiskHistory, Long>, GridRiskHistoryRepository {

	/** JpaCityCongestionObservationRepository.findLatestPerPlace 와 같은 형태다. */
	@Override
	@Query("""
			select history
			from GridRiskHistory history
			where history.windowEndedAt = (
				select max(latest.windowEndedAt)
				from GridRiskHistory latest
				where latest.gridCellId = history.gridCellId
			  )
			""")
	List<GridRiskHistory> findLatestPerGridCell();

	@Override
	@Query("""
			select history
			from GridRiskHistory history
			where history.gridCellId in :gridCellIds
			  and history.windowEndedAt = (
				select max(latest.windowEndedAt)
				from GridRiskHistory latest
				where latest.gridCellId = history.gridCellId
			  )
			""")
	List<GridRiskHistory> findLatestByGridCellIdIn(Collection<Long> gridCellIds);
}
