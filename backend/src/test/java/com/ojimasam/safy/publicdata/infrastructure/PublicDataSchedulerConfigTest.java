package com.ojimasam.safy.publicdata.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 수집 작업이 앱 공용 스케줄러(스레드 1개)가 아니라 전용 풀에서 돌아야 한다.
 * 공용 풀을 쓰면 최대 2분짜리 FastAPI 대기 동안 제보 라우팅·라이브 정리가 멈춘다.
 *
 * scheduler 이름은 문자열이라 오타가 나도 컴파일은 통과한다. 여기서 빈 이름과 함께 고정한다.
 */
class PublicDataSchedulerConfigTest {
	private static final String SCHEDULER_BEAN = "publicDataTaskScheduler";

	@Test
	void everyCollectionJobUsesDedicatedScheduler() {
		Method[] methods = PublicDataCollectionScheduler.class.getDeclaredMethods();

		long scheduledCount = 0;
		for (Method method : methods) {
			Scheduled scheduled = method.getAnnotation(Scheduled.class);
			if (scheduled == null) {
				continue;
			}
			scheduledCount++;
			assertThat(scheduled.scheduler())
					.as("%s 가 전용 스케줄러를 써야 한다", method.getName())
					.isEqualTo(SCHEDULER_BEAN);
		}

		assertThat(scheduledCount).as("혼잡도·행사·재난문자 3개").isEqualTo(3);
	}

	/** 빈 메서드 이름이 곧 빈 이름이므로, 이름이 바뀌면 @Scheduled 의 문자열도 깨진다. */
	@Test
	void schedulerBeanNameMatchesAnnotation() throws Exception {
		assertThat(PublicDataSchedulerConfig.class.getDeclaredMethod(SCHEDULER_BEAN, int.class))
				.isNotNull();
	}

	@Test
	void createsPoolWithConfiguredSize() {
		var scheduler = new PublicDataSchedulerConfig().publicDataTaskScheduler(3);

		assertThat(scheduler).isNotNull();
	}
}
