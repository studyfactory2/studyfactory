package com.example.studyfactory.domain.studyTime.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("학습시간 시분초 응답 테스트")
class StudyTimeDurationResponseTest {

    @Test
    @DisplayName("24시간을 넘는 누적시간도 자르지 않고 시분초와 전체 초를 함께 반환한다")
    void formatUnboundedHours() {
        StudyTimeDurationResponse response = StudyTimeDurationResponse.from(
                Duration.ofHours(41).plusMinutes(30).plusSeconds(7)
        );

        assertThat(response.totalSeconds()).isEqualTo(149_407L);
        assertThat(response.hours()).isEqualTo(41L);
        assertThat(response.minutes()).isEqualTo(30);
        assertThat(response.seconds()).isEqualTo(7);
        assertThat(response.formatted()).isEqualTo("41:30:07");
    }

    @Test
    @DisplayName("음수 시간은 응답으로 만들 수 없다")
    void rejectNegativeDuration() {
        assertThatThrownBy(() -> StudyTimeDurationResponse.from(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StudyTimeDurationResponse.fromTotalSeconds(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
