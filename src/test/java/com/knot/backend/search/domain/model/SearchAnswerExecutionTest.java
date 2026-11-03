package com.knot.backend.search.domain.model;

import com.knot.backend.search.domain.SearchException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SearchAnswerExecutionTest {
    private static final Instant NOW = Instant.parse("2026-10-11T00:00:00Z");
    private static final String TRACEPARENT = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";

    @Test
    @DisplayName("대기 작업을 중지하면 선점과 복구 대상에서 제외한다")
    void stop_success_queuedExecution() {
        SearchAnswerExecution execution = SearchAnswerExecution.queue(
                1,
                2,
                3,
                NOW,
                TRACEPARENT
        );
        execution.stop();
        assertThat(execution.getState()).isEqualTo(SearchExecutionState.FINISHED);
        assertThat(execution.getTraceparent()).isNull();
        assertThat(execution.getExecutionToken()).isNull();
        assertThat(execution.getDeadlineAt()).isNull();
        assertThat(execution.getStage()).isNull();
        assertThat(
                execution.claim(
                        UUID.randomUUID(),
                        NOW,
                        Duration.ofSeconds(10)
                )
        ).isFalse();
        assertThat(execution.isExpired(NOW.plusSeconds(20))).isFalse();
    }

    @Test
    @DisplayName("검색 실행은 traceparent를 보존하고 완료할 때 제거한다")
    void finish_success_clearsTraceparent() {
        // given
        SearchAnswerExecution execution = SearchAnswerExecution.queue(
                1,
                2,
                3,
                NOW,
                TRACEPARENT
        );
        assertThat(execution.getTraceparent()).isEqualTo(TRACEPARENT);
        execution.claim(
                UUID.randomUUID(),
                NOW,
                Duration.ofSeconds(10)
        );

        // when
        execution.finish();

        // then
        assertThat(execution.getTraceparent()).isNull();
    }

    @Test
    @DisplayName("재시도는 새 요청의 traceparent로 실행 문맥을 교체한다")
    void retry_success_replacesTraceparent() {
        // given
        String retryTraceparent = "00-fedcba9876543210fedcba9876543210-fedcba9876543210-01";
        SearchAnswerExecution execution = SearchAnswerExecution.queue(
                1,
                2,
                3,
                NOW,
                TRACEPARENT
        );
        execution.claim(
                UUID.randomUUID(),
                NOW,
                Duration.ofSeconds(10)
        );
        execution.finish();

        // when
        execution.retry(retryTraceparent);

        // then
        assertThat(execution.getState()).isEqualTo(SearchExecutionState.QUEUED);
        assertThat(execution.getTraceparent()).isEqualTo(retryTraceparent);
    }

    @Test
    @DisplayName("지원 길이를 넘는 traceparent는 거절한다")
    void queue_failure_traceparentTooLong() {
        // when & then
        assertThatExceptionOfType(SearchException.class).isThrownBy(
                () -> SearchAnswerExecution.queue(
                        1,
                        2,
                        3,
                        NOW,
                        "x".repeat(56)
                )
        );
    }

    @Test
    @DisplayName("실행 작업을 중지하면 기존 token과 기한을 보존하고 늦은 저장을 거절한다")
    void stop_success_runningExecution() {
        SearchAnswerExecution execution = SearchAnswerExecution.queue(
                1,
                2,
                3,
                NOW
        );
        UUID token = UUID.randomUUID();
        execution.claim(
                token,
                NOW,
                Duration.ofSeconds(10)
        );
        execution.stop();
        assertThat(execution.getState()).isEqualTo(SearchExecutionState.FINISHED);
        assertThat(execution.getExecutionToken()).isEqualTo(token);
        assertThat(
                execution.isCurrent(
                        token,
                        NOW
                )
        ).isFalse();
        assertThatExceptionOfType(SearchException.class).isThrownBy(() -> execution.recordRetrieval(0));
        assertThatExceptionOfType(SearchException.class).isThrownBy(execution::stop);
    }

    @Test
    @DisplayName("대기 작업은 token과 기한을 가진 RUNNING 작업으로 선점된다")
    void claim_success_tokenAndLease() {
        SearchAnswerExecution execution = queued();
        UUID token = UUID.randomUUID();
        assertThat(
                execution.claim(
                        token,
                        NOW,
                        Duration.ofSeconds(10)
                )
        ).isTrue();
        assertThat(execution.getExecutionToken()).isEqualTo(token);
        assertThat(execution.getDeadlineAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(execution.getState()).isEqualTo(SearchExecutionState.RUNNING);
    }

    @Test
    @DisplayName("선점된 작업은 다시 선점하지 않는다")
    void claim_failure_alreadyClaimed() {
        SearchAnswerExecution execution = claimed(UUID.randomUUID());
        assertThat(
                execution.claim(
                        UUID.randomUUID(),
                        NOW,
                        Duration.ofSeconds(10)
                )
        ).isFalse();
    }

    @Test
    @DisplayName("유효 token은 기한 안에서 현재 실행이다")
    void isCurrent_success_beforeDeadline() {
        UUID token = UUID.randomUUID();
        SearchAnswerExecution execution = claimed(token);
        assertThat(
                execution.isCurrent(
                        token,
                        NOW.plusSeconds(9)
                )
        ).isTrue();
    }

    @Test
    @DisplayName("다른 token은 현재 실행이 아니다")
    void isCurrent_failure_differentToken() {
        SearchAnswerExecution execution = claimed(UUID.randomUUID());
        assertThat(
                execution.isCurrent(
                        UUID.randomUUID(),
                        NOW
                )
        ).isFalse();
    }

    @Test
    @DisplayName("기한과 같은 시각의 token은 만료된 실행이다")
    void isCurrent_failure_atDeadline() {
        UUID token = UUID.randomUUID();
        SearchAnswerExecution execution = claimed(token);
        assertThat(
                execution.isCurrent(
                        token,
                        NOW.plusSeconds(10)
                )
        ).isFalse();
        assertThat(execution.isExpired(NOW.plusSeconds(10))).isTrue();
    }

    @Test
    @DisplayName("완료 실행은 FINISHED로 전환하고 이후 token 저장을 차단한다")
    void finish_success_fencesCallbacks() {
        UUID token = UUID.randomUUID();
        SearchAnswerExecution execution = claimed(token);
        execution.recordRetrieval(4);
        execution.finish();
        assertThat(execution.getExcludedCount()).isEqualTo(4);
        assertThat(execution.getStage()).isEqualTo(SearchProgressStage.GENERATING);
        assertThat(execution.getState()).isEqualTo(SearchExecutionState.FINISHED);
        assertThat(
                execution.isCurrent(
                        token,
                        NOW
                )
        ).isFalse();
    }

    @Test
    @DisplayName("완료 실행에는 검색 진행 상태를 기록할 수 없다")
    void recordRetrieval_failure_finished() {
        SearchAnswerExecution execution = claimed(UUID.randomUUID());
        execution.finish();
        assertThatExceptionOfType(SearchException.class).isThrownBy(() -> execution.recordRetrieval(1));
    }

    @Test
    @DisplayName("선점하지 않은 대기 실행은 완료할 수 없다")
    void finish_failure_queued() {
        SearchAnswerExecution execution = queued();
        assertThatExceptionOfType(SearchException.class).isThrownBy(execution::finish);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("0 또는 음수 실행 기한은 커스텀 오류로 거절한다")
    void claim_failure_invalidLease(long seconds) {
        SearchAnswerExecution execution = queued();
        assertThatExceptionOfType(SearchException.class).isThrownBy(
                () -> execution.claim(
                        UUID.randomUUID(),
                        NOW,
                        Duration.ofSeconds(seconds)
                )
        );
    }

    @Test
    @DisplayName("음수 제외 문서 수는 커스텀 오류로 거절한다")
    void recordRetrieval_failure_negativeCount() {
        SearchAnswerExecution execution = claimed(UUID.randomUUID());
        assertThatExceptionOfType(SearchException.class).isThrownBy(() -> execution.recordRetrieval(-1));
    }

    private SearchAnswerExecution queued() {
        return SearchAnswerExecution.queue(
                1,
                2,
                3,
                NOW
        );
    }

    private SearchAnswerExecution claimed(UUID token) {
        SearchAnswerExecution execution = queued();
        execution.claim(
                token,
                NOW,
                Duration.ofSeconds(10)
        );
        return execution;
    }
}
