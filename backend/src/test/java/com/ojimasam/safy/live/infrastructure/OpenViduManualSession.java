package com.ojimasam.safy.live.infrastructure;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import com.ojimasam.safy.live.application.LiveParticipantRole;
import com.ojimasam.safy.live.application.OpenViduProperties;

public final class OpenViduManualSession {
	private OpenViduManualSession() {
	}

	public static void main(String[] args) throws Exception {
		String url = required("OPENVIDU_URL");
		String apiKey = required("OPENVIDU_API_KEY");
		String apiSecret = required("OPENVIDU_API_SECRET");
		Duration manualSessionTtl = Duration.ofHours(2);
		OpenViduProperties properties = new OpenViduProperties(
				url, apiKey, apiSecret, manualSessionTtl, manualSessionTtl, Duration.ofMinutes(1),
				Duration.ofSeconds(60));
		LiveKitMediaGateway gateway = new LiveKitMediaGateway(properties,
				new com.ojimasam.safy.live.application.LiveRecordingProperties(
						false, "safy", "http://localhost:9000", "key", "secret", null, null, 0, 0, 0, null));
		String configuredRoomName = System.getenv("OPENVIDU_MANUAL_ROOM_NAME");
		String roomName = configuredRoomName == null || configuredRoomName.isBlank()
				? "safy-manual-" + UUID.randomUUID()
				: configuredRoomName;
		String configuredReporterIdentity = System.getenv("OPENVIDU_MANUAL_REPORTER_IDENTITY");
		String reporterIdentity = configuredReporterIdentity == null || configuredReporterIdentity.isBlank()
				? "manual-reporter"
				: configuredReporterIdentity;
		gateway.createRoom(roomName);

		String reporterToken = gateway.issueToken(
				roomName, reporterIdentity, LiveParticipantRole.REPORTER, manualSessionTtl);
		String viewerToken = gateway.issueToken(
				roomName, "manual-viewer", LiveParticipantRole.VIEWER, manualSessionTtl);

		Path output = Path.of("build", "openvidu-manual", "index.html").toAbsolutePath();
		Files.createDirectories(output.getParent());
		Files.writeString(output, page(url, roomName, reporterToken, viewerToken), StandardCharsets.UTF_8);
		System.out.println("Manual OpenVidu page generated: " + output);
		System.out.println("Room: " + roomName);
		System.out.println("Tokens expire in 2 hours. API credentials were not written to the page.");
	}

	private static String required(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
		return value;
	}

	private static String page(String url, String roomName, String reporterToken, String viewerToken) {
		return PAGE.formatted(json(url), json(roomName), json(reporterToken), json(viewerToken));
	}

	private static String json(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"")
				.replace("\r", "\\r").replace("\n", "\\n");
	}

	private static final String PAGE = """
			<!doctype html>
			<html lang="ko">
			<head>
			  <meta charset="utf-8">
			  <meta name="viewport" content="width=device-width,initial-scale=1">
			  <title>SAFY OpenVidu 실제 연결 테스트</title>
			  <style>
			    body { font-family: system-ui, sans-serif; margin:24px; background:#101827; color:#f8fafc; }
			    button { padding:12px 18px; margin-right:8px; border:0; border-radius:8px; cursor:pointer; }
			    #start { background:#22c55e; } #stop { background:#ef4444; color:white; }
			    .videos { display:grid; grid-template-columns:repeat(auto-fit,minmax(280px,1fr)); gap:16px; margin-top:20px; }
			    .card { background:#1e293b; padding:12px; border-radius:12px; }
			    video { width:100%%; min-height:220px; background:black; border-radius:8px; }
			    pre { white-space:pre-wrap; background:#0f172a; padding:12px; border-radius:8px; }
			  </style>
			</head>
			<body>
			  <h1>SAFY OpenVidu 실제 연결 테스트</h1>
			  <p>방: <code id="roomName"></code></p>
			  <p>역할: <strong id="roleName"></strong></p>
			  <button id="start">연결 시작</button>
			  <button id="stop" disabled>종료</button>
			  <div class="videos">
			    <div class="card" id="localCard"><h2>PC 제보자 송출 영상</h2><video id="local" autoplay muted playsinline></video></div>
			    <div class="card" id="remoteCard"><h2>휴대폰 운영자 수신 영상</h2><video id="remote" autoplay muted playsinline></video></div>
			  </div>
			  <h2>로그</h2><pre id="log">페이지 초기화 중</pre>
			  <script src="https://cdn.jsdelivr.net/npm/livekit-client/dist/livekit-client.umd.min.js"
			          onerror="document.getElementById('log').textContent='LiveKit SDK 로드 실패: CDN 연결을 확인하세요.'"></script>
			  <script>
			    const SERVER_URL = "%s";
			    const ROOM_NAME = "%s";
			    const REPORTER_TOKEN = "%s";
			    const VIEWER_TOKEN = "%s";
			    const role = new URLSearchParams(window.location.search).get('role');
			    const start = document.getElementById('start');
			    const stop = document.getElementById('stop');
			    document.getElementById('roomName').textContent = ROOM_NAME;
			    const log = (...args) => document.getElementById('log').textContent += '\\n' + args.join(' ');
			    window.addEventListener('error', (event) => log('JavaScript 오류:', event.message));
			    let room;

			    if (role !== 'reporter' && role !== 'viewer') {
			      document.getElementById('roleName').textContent = '역할 미지정';
			      document.getElementById('log').textContent = '주소에 ?role=reporter 또는 ?role=viewer를 붙여주세요.';
			      start.disabled = true;
			    } else {
			      const isReporter = role === 'reporter';
			      document.getElementById('roleName').textContent = isReporter ? '제보자(reporter)' : '운영자(viewer)';
			      document.getElementById('localCard').hidden = !isReporter;
			      document.getElementById('remoteCard').hidden = isReporter;
			      start.textContent = isReporter ? 'PC 카메라 송출 시작' : '제보자 영상 시청 시작';
			      document.getElementById('log').textContent = globalThis.LivekitClient
			        ? '준비 완료: 연결 시작 버튼을 눌러주세요.'
			        : 'LiveKit SDK를 불러오지 못했습니다. CDN 접근을 확인하세요.';
			      start.disabled = !globalThis.LivekitClient;
			    }

			    start.onclick = async () => {
			      start.disabled = true;
			      try {
			        const { Room, RoomEvent, Track } = LivekitClient;
			        room = new Room({ adaptiveStream: true, dynacast: true });
			        if (role === 'viewer') {
			          room.on(RoomEvent.TrackSubscribed, (track) => {
			            if (track.kind === Track.Kind.Video) {
			              track.attach(document.getElementById('remote'));
			              log('성공: PC 제보자 영상을 수신했습니다.');
			            }
			          });
			          await room.connect(SERVER_URL, VIEWER_TOKEN);
			          log('운영자 연결 성공: 제보자 영상을 기다리는 중입니다.');
			        } else {
			          await room.connect(SERVER_URL, REPORTER_TOKEN);
			          log('제보자 연결 성공');
			          const publication = await room.localParticipant.setCameraEnabled(true);
			          if (publication?.track) publication.track.attach(document.getElementById('local'));
			          log('PC 카메라 publish 성공 (마이크는 비활성화)');
			        }
			        stop.disabled = false;
			      } catch (error) {
			        log('실패:', error?.message || error);
			        start.disabled = false;
			      }
			    };
			    stop.onclick = async () => {
			      await room?.disconnect();
			      log('연결 종료');
			      start.disabled = false;
			      stop.disabled = true;
			    };
			  </script>
			</body>
			</html>
			""";
}
