package com.example.studyfactory.domain.preRegistration.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.preRegistration.dto.PreRegistrationCreateRequest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("사전등록 도메인 테스트")
class PreRegistrationTest {

    @Test
    @DisplayName("사전등록 요청 DTO로 사전등록 엔티티를 생성한다")
    void createPreRegistrationFromRequest() {
        PreRegistrationCreateRequest request = new PreRegistrationCreateRequest(
                1L,
                2L,
                " hong ",
                12,
                LocalDate.of(2026, 7, 1),
                3L,
                "아이스 아메리카노",
                "연하게",
                "오전 교육 예정"
        );

        PreRegistration preRegistration = request.toEntity();

        assertThat(preRegistration.getReferenceInformation().getBranchId()).isEqualTo(1L);
        assertThat(preRegistration.getReferenceInformation().getEmployeeTypeId()).isEqualTo(2L);
        assertThat(preRegistration.getReferenceInformation().getNameplateContentId()).isEqualTo(3L);
        assertThat(preRegistration.getName()).isEqualTo("hong");
        assertThat(preRegistration.getSeatNumber()).isEqualTo(12);
        assertThat(preRegistration.getExpectedJoinDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(preRegistration.getSubInformation().getDrinkSetting()).isEqualTo("아이스 아메리카노");
        assertThat(preRegistration.getSubInformation().getDrinkNote()).isEqualTo("연하게");
        assertThat(preRegistration.getSubInformation().getMemberNote()).isEqualTo("오전 교육 예정");
    }
}
