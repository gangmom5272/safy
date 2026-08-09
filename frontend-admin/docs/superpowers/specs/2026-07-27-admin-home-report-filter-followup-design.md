# SAFY 관리자 홈·제보 필터 추가 수정 설계

## 범위

기존 3차·4차 결과를 보존하면서 관리자 홈과 제보 목록 필터만 수정한다. 사용자 앱, 제보 상세, 지도 전체 페이지, 모니터링 전체 페이지, 인증과 백엔드는 변경하지 않는다. Primary는 기존 `#004AAD` 변수를 유지한다.

## 홈

하나의 CSS grid에 지도, 최근 위험 제보, 실시간 연결을 배치한다. 지도는 좌측 1행, 최근 제보는 좌측 2행, 실시간 연결은 우측에서 두 행을 차지한다. 1180px 이하에서는 지도, 실시간 연결, 최근 제보 순서의 한 열로 전환한다.

실시간 연결은 기존 `WebRTCConnectionStatus`의 `live`, `waiting`, `requested` 데이터를 표시한다. 각 카드는 장소/위험 badge, 16:9 media, 요청 form으로 구성한다. 상태가 `live`면 `요청 전송`, 아니면 `연결`을 표시한다.

관리자 앱과 백엔드에는 연결 요청 API, 송신 WebSocket 또는 DataChannel이 없다. 사용자 앱에는 상태를 수신하는 WebSocket만 있고 송신 규격은 없다. 따라서 입력값은 trim하고 200자로 제한하되, submit 시 성공이나 상태 전환을 만들지 않고 연결되지 않은 기능을 명확한 오류로 표시한다. 추후 실제 transport를 연결할 handler 경계를 카드 컴포넌트에 둔다.

## 제보 필터

필터의 `AdminSection` wrapper를 제거해 중복 제목과 흰 카드 배경을 없앤다. form 상단 우측에 inline SVG refresh button과 적용 button을 두고, 다음 행에 검색어 2fr와 나머지 1fr 필드를 둔다. 상세 조건 toggle은 기본 필터 아래 우측에 둔다.

초기화는 draft와 applied 상태를 모두 초기값으로 바꿔 client-side 목록과 결과 건수를 즉시 복원한다. placeholder만 `제보 ID, 내용, 제보자명`으로 바꾸며 주소 검색 로직은 유지한다.

## 검증

`npm run lint`, `npm run build`, 제거 대상 문자열 검색, dev 서버 HTTP smoke test를 수행한다. 별도 test/typecheck script는 없다. 브라우저에서는 1920/1440/1280 grid, 상세 조건, 필터 적용·초기화, WebRTC 내부 scroll과 미연결 오류를 확인한다.
