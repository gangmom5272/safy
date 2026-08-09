# SAFY 관리자 홈 4차 UI 수정 설계

## 범위

기존 3차 변경을 유지하면서 관리자 홈, 공통 상단 헤더, 관리자 프로필 드롭다운만 수정한다. 제보 목록·상세, 사용자 페이지, 라우팅, 인증, WebRTC 데이터와 타입은 변경하지 않는다. Primary `#004AAD`와 기존 긴급 알림의 빨간색 의미 체계도 유지한다.

## 현재 구조와 원인

홈은 첫 행 `실시간 위험도 지도 / 실시간 연결`, 둘째 행 `최근 위험 제보 / WebRTC 연결 대기열`의 두 grid로 구성되어 있다.

WebRTC 영상 placeholder인 `.video-card__frame`은 absolute positioning으로 문서 흐름에서 높이를 차지하지 않는다. 같은 카드의 장소명과 연결 시간 위로 겹칠 수 있으며, `.video-card` 자체에 적용된 `aspect-ratio`는 확장된 너비에서 카드 전체 높이를 과도하게 키운다.

지도는 흰색 배경과 16px padding을 가진 공통 `.admin-card` 안에 border가 있는 `.map-preview`가 중첩되어 있어 지도 바깥에 흰색 프레임이 보인다.

프로젝트에는 아이콘 라이브러리가 없으며 기존 공통 아이콘에도 로그아웃과 사이렌 도형이 없다. 새 의존성을 추가하지 않고 헤더 전용의 선형 inline SVG 컴포넌트를 사용한다.

## 홈 레이아웃

홈을 다음 순서로 재배치한다.

1. 첫 행: 실시간 위험도 지도 / 최근 위험 제보
2. 둘째 행: 실시간 연결 전체 너비

WebRTC 연결 대기열 JSX, 홈 전용 waiting filter, 요청 state, handler와 관련 import를 제거한다. 원본 mock 데이터, WebRTC 타입과 다른 페이지의 기능은 유지한다.

실시간 연결 섹션은 전체 너비에서 내부 grid를 사용한다. 넓은 화면에서는 `repeat(auto-fit, minmax(280px, 1fr))`로 두 개 이상의 독립 카드를 배치하고, 작은 화면에서는 한 열로 전환한다. 목록 컨테이너의 제한 높이와 `overflow-y: auto`, `scrollbar-gutter: stable`은 유지한다.

각 연결 카드는 정상 흐름의 header, media, metadata 영역으로 나눈다. 장소명과 위험 badge는 header에 두고, media만 `width: 100%`와 `aspect-ratio: 16 / 9`를 가진다. LIVE badge는 media 내부에 배치한다. 카드 자체에는 absolute inset이나 aspect ratio를 사용하지 않고 `min-width: 0`, `overflow: hidden`을 적용한다.

## 지도

공통 section의 외부 제목 구조는 유지한다. 홈 지도에만 적용되는 card class에서 padding을 0으로 만들고 지도 button의 border를 제거한다. 지도는 `width: 100%`, `height: 100%`, `min-height`를 사용해 전체 카드 영역을 채우며 외부 카드 radius를 상속한다. 현재 지도는 CSS mock preview이므로 지도 SDK resize 또는 `invalidateSize` 호출은 필요하지 않다. marker와 route 이동 동작은 유지한다.

## 헤더와 아이콘

관리자 profile trigger에서 `⌄` 문자와 그 주변 공백을 제거한다. 동적 표시명, 전체 버튼 클릭, hover/focus, 외부 클릭 닫기, Escape 닫기는 유지한다.

드롭다운 메뉴는 `LogOutIcon`과 `로그아웃` 텍스트를 가로 정렬한다. 기존 `handleLogout`을 그대로 호출하며 확인창은 추가하지 않는다.

긴급 알림 버튼은 `SirenIcon`, 알림 문구, 기존 danger report count를 가로 정렬한다. icon은 `aria-hidden`으로 처리하고 button의 기존 동적 `aria-label`과 모달 동작을 유지한다.

inline SVG는 `currentColor`, 18px, round linecap/linejoin과 약 1.8 선 두께를 사용해 기존 텍스트 버튼 스타일과 맞춘다.

## 반응형과 검증

1920px과 1280px에서 실시간 연결 내부 grid가 겹치지 않아야 한다. 좁은 화면에서는 연결 카드가 한 열이 되고 header action 영역도 기존 반응형 동작을 유지한다. 긴 장소명은 `overflow-wrap` 또는 ellipsis로 카드 너비를 침범하지 않게 한다.

프로젝트에 존재하는 `npm run lint`와 `npm run build`만 실행한다. 별도 `typecheck` script는 없다. 제거 대상 문자열과 queue 관련 홈 전용 symbol을 검색하고, dev 서버 HTTP 응답을 확인한다. 브라우저에서는 카드 grid, 내부 스크롤, 지도 프레임 제거, dropdown 닫기와 logout, 긴급 알림 modal을 직접 확인한다.
