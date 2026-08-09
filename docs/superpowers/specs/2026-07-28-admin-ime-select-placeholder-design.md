# SAFY 관리자 IME 및 Select Placeholder 수정 설계

## 목표와 범위

현재 누적된 관리자 UI 변경을 유지하면서 실시간 모니터링의 한글 지역 검색 오류와 모니터링·위험도 지도의 placeholder 표현만 수정한다. WebRTC, 그리드, 지도 마커, 레이어, API 경로 및 관련 없는 화면은 변경하지 않는다.

## 실시간 모니터링 지역 검색

현재 Input의 value는 URL `keyword` 검색 파라미터에 직접 연결되어 있고 `onChange`마다 `setSearchParams`가 실행된다. 한글 IME 조합 중에도 React Router 내비게이션 상태가 갱신되어 controlled input 값이 URL에서 다시 공급되는 것이 입력 불안정의 원인이다. 동적 key, debounce, 목록 갱신에 의한 초기화는 존재하지 않는다.

입력 중인 값은 별도 `regionInput` local state로 관리한다. `onChange`는 trim이나 정규화 없이 원문을 저장하고 검색이나 URL 갱신을 실행하지 않는다. form submit 또는 Enter 시점에만 trim한 값을 URL `keyword`에 반영한다. 입력 중 라우터 갱신이 없어 IME 조합 자체를 방해하지 않으므로 composition 상태와 debounce는 추가하지 않는다.

검색창 오른쪽에는 명시적인 X 버튼을 배치한다. X 버튼은 local 입력과 URL 적용 검색어를 함께 제거한다. 초기화 버튼은 X 동작에 더해 위험도와 연결 상태를 placeholder 상태로 복원한다. 외부 URL keyword로 페이지에 진입한 경우 local state 초기값으로 사용한다.

## Select Placeholder

모니터링 위험도와 연결 상태, 위험도 지도 위험도는 빈 문자열을 placeholder 상태로 사용한다. placeholder option은 disabled이며 각각 `위험도`, `연결 상태`를 표시한다. `전체`는 별도의 실제 옵션으로 유지한다. 빈 값은 전체 조회 조건으로 처리하며 API나 필터 enum 값으로 전달하지 않는다.

각 Select는 값이 빈 문자열일 때 `select-placeholder` 클래스를 적용한다. 이 클래스는 Input placeholder와 동일한 기존 토큰 `var(--text-muted)`를 사용한다. 실제 옵션 선택 후에는 기존 `var(--text)` 색상을 사용한다. 페이지 전용 클래스로 적용해 다른 관리자 Select에는 영향을 주지 않는다.

## 위험도 지도 위치 아이콘

위험도 지도 지역 검색창을 상대 위치 wrapper로 감싸고 실시간 모니터링과 동일한 SVG 경로와 `monitoring-filter__location-icon` 스타일을 재사용한다. Input 왼쪽 padding을 38px로 설정해 아이콘과 텍스트가 겹치지 않게 한다. 기존 draft/applied 필터와 Enter submit 흐름은 유지한다.

위험도 지도 초기 위험도는 빈 문자열이며 전체 마커를 표시한다. 초기화하면 빈 placeholder 상태와 전체 결과로 복원된다. 사용자가 `전체`를 선택하면 기존 all 값으로 전체 결과를 표시한다.

## 접근성과 반응형

검색 form과 X 버튼에는 각각 검색 및 초기화 aria-label을 제공한다. X 아이콘은 장식용으로 숨긴다. 기존 한 줄 필터와 작은 화면 wrap 규칙은 유지한다.

## 검증 기준

- `npm run lint`와 `npm run build`가 성공한다.
- 입력 onChange 경로에서 URL 갱신과 문자열 변형이 제거된다.
- Enter submit이 local 값을 URL 적용 검색어로 반영한다.
- X와 초기화가 local 및 적용 검색어를 제거한다.
- 세 Select의 빈 상태는 muted 색상이고 실제 선택값은 본문 색상이다.
- 위험도 지도 위치 아이콘, 기존 필터 submit, 전체 옵션과 초기화가 유지된다.
- 개발 서버가 응답하며 브라우저에서 한글 연속 입력, X, Select 색상 전환과 기존 WebRTC·지도 동작을 확인한다.
