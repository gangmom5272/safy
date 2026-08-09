package com.ojimasam.safy.notification.infrastructure;

public enum WebPushSendResult {
	// 푸시 서비스가 메시지를 접수했다.
	SUCCESS,
	// 구독이 더 이상 유효하지 않다. 재시도하지 않고 구독을 만료 처리한다.
	EXPIRED,
	// 같은 요청으로는 성공할 수 없다(잘못된 요청, VAPID 불일치, 직렬화 실패). 재시도하지 않는다.
	REJECTED,
	// 일시적 장애다(네트워크 오류, 5xx, 429). 재시도 대상이다.
	FAILED;

	public static WebPushSendResult fromHttpStatus(int status) {
		if (status >= 200 && status < 300) return SUCCESS;
		if (status == 404 || status == 410) return EXPIRED;
		if (status == 429) return FAILED;
		if (status >= 400 && status < 500) return REJECTED;
		return FAILED;
	}
}
