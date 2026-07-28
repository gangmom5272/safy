// 디자인/퍼블리싱 작업용 목 모드.
// 백엔드가 없거나 DB가 비어 있어도 화면이 채워지도록 API 레이어에서 목 데이터를 반환한다.
// 켜는 법: npm run dev:mock (또는 .env 에 VITE_USE_MOCK_API=true)
// 명시적으로 'true' 일 때만 켜지므로 빌드·CI 는 항상 실제 API 를 쓴다.
export const USE_MOCK_API = import.meta.env.VITE_USE_MOCK_API === 'true'

// 목 응답에 약간의 지연을 줘서 로딩 상태도 확인할 수 있게 한다.
export function mockResponse<T>(data: T, delay = 180): Promise<T> {
  return new Promise((resolve) => {
    window.setTimeout(() => resolve(data), delay)
  })
}
