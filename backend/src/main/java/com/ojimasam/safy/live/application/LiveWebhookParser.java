package com.ojimasam.safy.live.application;

/**
 * 미디어 서버 웹훅 본문의 서명을 검증하고 {@link LiveWebhookEvent}로 변환한다.
 */
public interface LiveWebhookParser {
	/**
	 * @throws LiveMediaException 서명 검증에 실패했거나 본문을 해석할 수 없는 경우
	 */
	LiveWebhookEvent parse(String rawBody, String authorizationHeader);
}
