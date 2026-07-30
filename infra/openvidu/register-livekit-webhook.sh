#!/bin/bash
# LiveKit이 우리 backend로도 웹훅을 보내도록 등록하는 스크립트. 서버에서 sudo로 실행한다.
#
# 왜 필요한가:
#   Egress(녹화)는 backend가 PARTICIPANT_JOINED 웹훅을 받아야 시작된다
#   (LiveWebhookService의 case PARTICIPANT_JOINED -> recordingService.startForSession).
#   그런데 LiveKit의 webhook.urls에는 OpenVidu Meet 주소 하나만 등록돼 있어
#   우리 backend는 이벤트를 한 번도 받지 못했다. 그래서 stream_recordings가 0행이다
#   (2026-07-30 실측). URL을 추가하는 것은 가산이므로 Meet 동작에는 영향이 없다.
#
# 왜 저장소 밖을 고치면서 스크립트를 두는가:
#   대상 파일은 /opt/openvidu 아래라 우리 CI의 rsync 대상이 아니다. 배포로 지워지지도
#   않지만 git에 남지도 않는다. OpenVidu 업그레이드나 서버 재구축 때 조용히 사라지므로,
#   무엇을 왜 바꿨는지를 저장소에 남기고 한 번에 복구할 수 있게 스크립트로 만든다.
#   CI는 이 스크립트를 실행하지 않는다 — OpenVidu 스택은 수동으로만 건드린다.
#
# 사용법:
#   sudo bash register-livekit-webhook.sh
#
# 되돌리기:
#   실행 시 두 파일의 .bak 경로를 출력한다. 그 파일들을 제자리에 복사하고
#   docker restart openvidu 하면 원복된다.
#
# 재실행 안전(멱등). OpenVidu 업그레이드로 설정이 초기화되면 다시 실행하면 된다.

set -euo pipefail

WEBHOOK_URL="http://127.0.0.1:6080/api/v1/live/webhooks/livekit"

# 두 파일을 모두 고쳐야 한다.
#   TEMPLATE : ${openvidu.*} 치환 전 원본. 전체 스택 재기동 때 RUNTIME으로 렌더링된다.
#   RUNTIME  : 컨테이너에 /config 로 마운트되는 실제 파일. 이것만 고치면 즉시 적용되지만
#              다음 전체 재기동 때 TEMPLATE으로부터 다시 렌더링되며 사라진다.
TEMPLATE="/opt/openvidu/config/livekit.yaml"
RUNTIME="/opt/openvidu/data/runtime/config/openvidu/livekit.yaml"

if [ "$(id -u)" -ne 0 ]; then
  echo "root 권한이 필요합니다: sudo bash $0"
  exit 1
fi

# 6080은 우리 nginx다. backend(8090)로 바로 보내지 않고 nginx를 거치게 해서
# 웹훅 수신 여부가 nginx access 로그에도 남게 한다(LOGGING.md 참고).
echo "등록할 URL: $WEBHOOK_URL"

STAMP=$(date -u +%Y%m%d%H%M%S)
CHANGED=0

for FILE in "$TEMPLATE" "$RUNTIME"; do
  if [ ! -f "$FILE" ]; then
    echo "파일이 없습니다: $FILE"
    exit 1
  fi

  if grep -qF "$WEBHOOK_URL" "$FILE"; then
    echo "이미 등록됨, 건너뜀: $FILE"
    continue
  fi

  if ! grep -q "/livekit/webhook" "$FILE"; then
    echo "webhook.urls 항목을 찾지 못했습니다: $FILE"
    echo "OpenVidu가 설정 구조를 바꿨을 수 있습니다. 수동 확인이 필요합니다."
    exit 1
  fi

  cp -a "$FILE" "$FILE.bak.$STAMP"

  # 기존 URL 줄 바로 아래에 같은 들여쓰기로 한 줄 추가한다.
  # 들여쓰기는 하드코딩하지 않고 매칭된 줄에서 그대로 가져온다.
  awk -v url="$WEBHOOK_URL" '
    { print }
    !added && /\/livekit\/webhook/ {
      match($0, /^[ ]*/)
      print substr($0, 1, RLENGTH) "- " url
      added = 1
    }
  ' "$FILE" > "$FILE.tmp"

  mv "$FILE.tmp" "$FILE"
  echo "추가함: $FILE   (백업: $FILE.bak.$STAMP)"
  CHANGED=1
done

if [ "$CHANGED" -eq 0 ]; then
  echo "변경 사항이 없습니다. 재기동하지 않습니다."
  exit 0
fi

# systemctl restart openvidu 를 쓰지 않는다. 그 유닛은 ExecStartPre=docker-compose down
# 이라 Caddy까지 내려가고, Caddy가 443을 물고 있어 우리 앱까지 함께 끊긴다.
# livekit-server 컨테이너만 재시작하면 진행 중인 라이브 세션과 Meet 방만 끊긴다.
echo "openvidu(livekit-server) 컨테이너를 재시작합니다..."
docker restart openvidu >/dev/null

echo "컨테이너가 읽고 있는 최종 설정:"
sleep 3
docker exec openvidu grep -A6 '^webhook:' /config/livekit.yaml

echo
echo "완료. 라이브 제보를 한 번 실행한 뒤 아래로 수신을 확인한다."
echo "  Grafana Explore:  {container=\"infra-nginx-1\"} |= \"/api/v1/live/webhooks/livekit\""
