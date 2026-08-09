/**
 * 관제 경보음.
 *
 * 오디오 파일을 두지 않고 Web Audio 로 짧은 비프를 만든다. 번들에 자산이 늘지 않고,
 * 관제실 환경에 맞춰 길이·음높이를 코드에서 바로 조정할 수 있다.
 *
 * 한계: 브라우저 자동재생 정책상 사용자가 페이지와 한 번이라도 상호작용하기 전에는
 * AudioContext 가 suspended 로 시작한다. 관제사가 화면만 띄워두고 클릭한 적이 없으면
 * 소리가 나지 않는다. 코드로 우회할 수 없는 제약이므로 시각 경보(깜빡임)가 항상 함께
 * 동작해야 하고, 소리만으로 위험을 알리는 설계를 해서는 안 된다.
 */

// 호출마다 새로 만들면 브라우저가 허용하는 AudioContext 개수를 금방 소진한다.
let sharedContext: AudioContext | null = null

export function playRiskAlert(): void {
  if (typeof window === 'undefined' || typeof window.AudioContext === 'undefined') return

  try {
    sharedContext ??= new window.AudioContext()
    const context = sharedContext
    // 상호작용 이후 첫 호출이면 여기서 풀린다. 그 전이라면 재생되지 않고 조용히 끝난다.
    if (context.state === 'suspended') void context.resume()

    const startedAt = context.currentTime
    const oscillator = context.createOscillator()
    const gain = context.createGain()

    oscillator.type = 'sine'
    oscillator.frequency.setValueAtTime(880, startedAt)
    // 게인을 0 에서 올렸다 내린다. 갑자기 켜고 끄면 딸깍하는 잡음이 함께 들린다.
    gain.gain.setValueAtTime(0, startedAt)
    gain.gain.linearRampToValueAtTime(0.18, startedAt + 0.02)
    gain.gain.linearRampToValueAtTime(0, startedAt + 0.3)

    oscillator.connect(gain)
    gain.connect(context.destination)
    oscillator.start(startedAt)
    oscillator.stop(startedAt + 0.32)
  } catch {
    // 소리는 보조 수단이다. 실패해도 시각 경보는 그대로 동작해야 하므로 삼킨다.
  }
}
