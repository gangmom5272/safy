package com.ojimasam.safy.admin.application.event;

import com.ojimasam.safy.admin.domain.type.OperatorPresenceStatus;

/**
 * 담당자가 제보를 받을 수 없는 상태로 전환됨(자리비움 또는 오프라인).
 * report 쪽 리스너가 미확인(ASSIGNED) 배정을 회수해 재배정한다.
 *
 * <p>확인 후 처리중(IN_PROGRESS)인 배정은 실제 대응 중일 수 있으므로 여기서 건드리지
 * 않는다. 연결까지 끊긴 채 유예가 지나면 근무 상태 스위퍼가 따로 회수한다.
 *
 * <p>회수 사유를 report 쪽 enum 으로 담지 않고 근무 상태를 그대로 실어 보낸다.
 * admin 이 report 에 의존하지 않도록 사유 변환은 리스너가 한다.
 */
public record OperatorUnavailableEvent(Long operatorId, OperatorPresenceStatus status) {
}
