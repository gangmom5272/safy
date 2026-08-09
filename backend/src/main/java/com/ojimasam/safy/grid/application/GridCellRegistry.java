package com.ojimasam.safy.grid.application;

import java.math.BigDecimal;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojimasam.safy.grid.domain.GridCell;
import com.ojimasam.safy.grid.domain.GridCellCode;
import com.ojimasam.safy.grid.domain.GridCellRepository;

/**
 * 좌표가 속한 격자 셀 행을 돌려준다. 행이 없으면 만든다.
 *
 * <p>서울 전역 셀을 미리 적재하지 않으므로 셀 행은 제보가 처음 닿는 순간 생긴다.
 * 셀이 속할 자리는 {@link GridCellCode} 산식으로 이미 정해져 있어서, 언제 만들어도
 * 같은 위치·같은 크기의 셀이 된다.
 */
@Service
public class GridCellRegistry {
	private final GridCellRepository gridCellRepository;
	private final GridCellCreator gridCellCreator;

	public GridCellRegistry(GridCellRepository gridCellRepository, GridCellCreator gridCellCreator) {
		this.gridCellRepository = gridCellRepository;
		this.gridCellCreator = gridCellCreator;
	}

	@Transactional
	public GridCell findOrCreate(BigDecimal latitude, BigDecimal longitude) {
		return findOrCreate(GridCellCode.of(latitude, longitude));
	}

	@Transactional
	public GridCell findOrCreate(GridCellCode code) {
		return gridCellRepository.findByGridCode(code.value())
				.orElseGet(() -> create(code));
	}

	private GridCell create(GridCellCode code) {
		try {
			// 별도 트랜잭션에서 삽입한다. 같은 셀에 동시 제보가 들어와 충돌해도
			// 롤백되는 것은 안쪽 트랜잭션뿐이라 이 트랜잭션은 그대로 쓸 수 있다.
			gridCellCreator.create(code);
		} catch (DataIntegrityViolationException exception) {
			// 다른 요청이 먼저 만들었다 — 아래에서 그 행을 읽어 쓴다.
		}

		return gridCellRepository.findByGridCode(code.value())
				.orElseThrow(() -> new IllegalStateException("격자 셀 저장에 실패했습니다."));
	}
}
