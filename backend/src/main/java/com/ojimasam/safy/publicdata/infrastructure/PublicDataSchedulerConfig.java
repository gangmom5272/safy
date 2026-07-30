package com.ojimasam.safy.publicdata.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 공공데이터 수집 전용 스케줄러.
 *
 * 왜 별도 풀인가:
 *   Spring 의 기본 스케줄러 스레드 풀은 1개이고, 이 애플리케이션의 @Scheduled 는 모두 그것을 공유한다
 *   (제보 라우팅 30초, 제보 배정 30초, 라이브 세션 정리 20초·1분, 미디어 정리 5분, SSE 브로드캐스트).
 *   수집 작업은 FastAPI 응답을 최대 read-timeout(2분)까지 기다리므로,
 *   같은 스레드를 쓰면 그 시간 동안 제보 라우팅과 라이브 정리가 멈춘다.
 *
 * 풀 크기는 이 스케줄러가 돌리는 작업 수(혼잡도·행사·재난문자)에 맞춘다.
 * 같은 작업이 동시에 두 번 실행되지는 않으므로(스케줄러가 작업 단위로 직렬화) 그 이상은 필요 없다.
 */
@Configuration
public class PublicDataSchedulerConfig {
	@Bean
	TaskScheduler publicDataTaskScheduler(
			@Value("${app.public-data.scheduler-pool-size:3}") int poolSize
	) {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(poolSize);
		scheduler.setThreadNamePrefix("public-data-");
		// 종료 시 진행 중인 수집을 기다리지 않는다.
		// 응답을 받기 전에 끊기면 아무것도 저장되지 않고, 적재 중이면 트랜잭션이 롤백된다.
		// 어느 쪽이든 다음 주기에 다시 수집하므로, 배포 시 종료를 지연시킬 이유가 없다.
		return scheduler;
	}
}
