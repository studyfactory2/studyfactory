package com.example.studyfactory.domain.nameplate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.studyfactory.domain.nameplate.dto.NameplateContentCreateRequest;
import com.example.studyfactory.domain.nameplate.dto.NameplateContentResponse;
import com.example.studyfactory.domain.nameplate.entity.NameplateContent;
import com.example.studyfactory.domain.nameplate.exception.NameplateContentException;
import com.example.studyfactory.domain.nameplate.repository.NameplateContentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("명패내용 서비스 테스트")
class NameplateContentServiceTest {

    @InjectMocks
    private NameplateContentService nameplateContentService;

    @Mock
    private NameplateContentRepository nameplateContentRepository;

    @Test
    @DisplayName("명패내용을 저장하고 응답을 반환한다")
    void createNameplateContent() {
        NameplateContentCreateRequest request = new NameplateContentCreateRequest(" 홍길동 매니저 ");
        given(nameplateContentRepository.existsByContent("홍길동 매니저")).willReturn(false);
        given(nameplateContentRepository.save(any(NameplateContent.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        NameplateContentResponse response = nameplateContentService.create(request);

        assertThat(response.content()).isEqualTo("홍길동 매니저");
        verify(nameplateContentRepository).save(any(NameplateContent.class));
    }

    @Test
    @DisplayName("이미 등록된 명패내용이면 예외가 발생한다")
    void throwExceptionWhenNameplateContentIsDuplicated() {
        NameplateContentCreateRequest request = new NameplateContentCreateRequest("홍길동 매니저");
        given(nameplateContentRepository.existsByContent("홍길동 매니저")).willReturn(true);

        assertThatThrownBy(() -> nameplateContentService.create(request))
                .isInstanceOf(NameplateContentException.class)
                .hasMessageContaining("이미 등록된 명패내용입니다.");
    }
}
