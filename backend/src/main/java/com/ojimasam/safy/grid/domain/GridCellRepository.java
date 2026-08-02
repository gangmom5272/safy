package com.ojimasam.safy.grid.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GridCellRepository {
	Optional<GridCell> findByGridCode(String gridCode);

	List<GridCell> findAllByGridCodeIn(Collection<String> gridCodes);

	GridCell save(GridCell gridCell);
}
