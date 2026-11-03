package com.knot.backend.search.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchIntentPromptTest {

    @Test
    @DisplayName("구체적인 Knot 정책과 동작은 회의 검색으로 분류한다")
    void system_routesSpecificProductFactsToMeetingSearch() {
        // given
        String prompt = SearchIntentPrompt.system();

        // then
        assertThat(prompt).contains("\"탐색 중에도 녹음이 계속되나요?\" MEETING_SEARCH")
                .contains("\"Workspace 문서는 누가 접근할 수 있나요?\" MEETING_SEARCH")
                .contains("\"답변 근거를 찾을 때 문서 도구를 어떻게 사용하나요?\" MEETING_SEARCH")
                .contains("\"조건을 지정해 문서를 찾고, 후보가 부족하면 어떻게 더 탐색하나요?\" MEETING_SEARCH")
                .contains("\"이 문서는 실제 회의 기록이나 운영 성능 수치인가요?\" MEETING_SEARCH");
    }

    @Test
    @DisplayName("일반적인 Knot 사용 방법만 서비스 안내로 분류한다")
    void systemRoutesGenericUsageToServiceGuide() {
        // given
        String prompt = SearchIntentPrompt.system();

        // then
        assertThat(prompt).contains("\"Knot에서 문서 검색을 어떻게 실행해요?\" SERVICE_GUIDE");
    }

    @Test
    @DisplayName("기존 회의 내용을 정리하는 질문은 새 산출물 작성과 구분해 회의 검색으로 분류한다")
    void systemRoutesSummaryOfExistingPlansToMeetingSearch() {
        // given
        String prompt = SearchIntentPrompt.system();

        // then
        assertThat(prompt).contains("\"백업 일정과 복구 훈련 계획, 신규 및 기존 데이터베이스 백업의 암호화 상태를 정리해줘.\" MEETING_SEARCH")
                .contains("\"백업 복구 계획을 새로 작성해줘\" OUT_OF_SCOPE");
    }
}
