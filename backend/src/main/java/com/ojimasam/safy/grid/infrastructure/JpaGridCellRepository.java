package com.ojimasam.safy.grid.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ojimasam.safy.grid.domain.GridCell;
import com.ojimasam.safy.grid.domain.GridCellRepository;

public interface JpaGridCellRepository extends JpaRepository<GridCell, Long>, GridCellRepository {
}
