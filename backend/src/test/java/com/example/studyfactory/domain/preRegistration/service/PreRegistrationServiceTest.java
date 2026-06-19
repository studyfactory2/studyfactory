package com.example.studyfactory.domain.preRegistration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.studyfactory.domain.branch.repository.BranchRepository;
import com.example.studyfactory.domain.nameplate.repository.NameplateContentRepository;
import com.example.studyfactory.domain.preRegistration.dto.PreRegistrationCreateRequest;
import com.example.studyfactory.domain.preRegistration.dto.PreRegistrationResponse;
import com.example.studyfactory.domain.preRegistration.entity.PreRegistration;
import com.example.studyfactory.domain.preRegistration.exception.PreRegistrationException;
import com.example.studyfactory.domain.preRegistration.repository.PreRegistrationRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("사전등록 서비스 테스트")
class PreRegistrationServiceTest {

    @InjectMocks
    private PreRegistrationService preRegistrationService;

    @Mock
    private PreRegistrationRepository preRegistrationRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private NameplateContentRepository nameplateContentRepository;

    @Test
    @DisplayName("사전등록 요청을 저장하고 응답을 반환한다")
    void createPreRegistration() {
        PreRegistrationCreateRequest request = createRequest();
        given(branchRepository.existsById(1L)).willReturn(true);
        given(nameplateContentRepository.existsById(3L)).willReturn(true);
        given(preRegistrationRepository.save(any(PreRegistration.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        PreRegistrationResponse response = preRegistrationService.create(request);

        assertThat(response.branchId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("hong");
        assertThat(response.seatNumber()).isEqualTo(12);
        assertThat(response.expectedJoinDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(response.nameplateContentId()).isEqualTo(3L);
        assertThat(response.drinkSetting()).isEqualTo("아이스 아메리카노");
        assertThat(response.drinkNote()).isEqualTo("연하게");
        assertThat(response.memberNote()).isEqualTo("오전 교육 예정");
        verify(preRegistrationRepository).save(any(PreRegistration.class));
    }

    @Test
    @DisplayName("존재하지 않는 지점이면 예외가 발생한다")
    void throwExceptionWhenBranchDoesNotExist() {
        PreRegistrationCreateRequest request = createRequest();
        given(branchRepository.existsById(1L)).willReturn(false);

        assertThatThrownBy(() -> preRegistrationService.create(request))
                .isInstanceOf(PreRegistrationException.class)
                .hasMessageContaining("존재하지 않는 지점입니다.");
    }

    @Test
    @DisplayName("존재하지 않는 명패내용이면 예외가 발생한다")
    void throwExceptionWhenNameplateContentDoesNotExist() {
        PreRegistrationCreateRequest request = createRequest();
        given(branchRepository.existsById(1L)).willReturn(true);
        given(nameplateContentRepository.existsById(3L)).willReturn(false);

        assertThatThrownBy(() -> preRegistrationService.create(request))
                .isInstanceOf(PreRegistrationException.class)
                .hasMessageContaining("존재하지 않는 명패내용입니다.");
    }

    private PreRegistrationCreateRequest createRequest() {
        return new PreRegistrationCreateRequest(
                1L,
                " hong ",
                12,
                LocalDate.of(2026, 7, 1),
                3L,
                "아이스 아메리카노",
                "연하게",
                "오전 교육 예정"
        );
    }
}
