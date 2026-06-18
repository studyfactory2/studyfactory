package com.example.studyfactory.domain.employeeType.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.studyfactory.domain.employeeType.dto.EmployeeTypeCreateRequest;
import com.example.studyfactory.domain.employeeType.dto.EmployeeTypeResponse;
import com.example.studyfactory.domain.employeeType.entity.EmployeeType;
import com.example.studyfactory.domain.employeeType.exception.EmployeeTypeException;
import com.example.studyfactory.domain.employeeType.repository.EmployeeTypeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("사원구분 서비스 테스트")
class EmployeeTypeServiceTest {

    @InjectMocks
    private EmployeeTypeService employeeTypeService;

    @Mock
    private EmployeeTypeRepository employeeTypeRepository;

    @Test
    @DisplayName("사원구분 이름을 저장하고 응답을 반환한다")
    void createEmployeeType() {
        EmployeeTypeCreateRequest request = new EmployeeTypeCreateRequest(" 정규직 ");
        given(employeeTypeRepository.existsByName("정규직")).willReturn(false);
        given(employeeTypeRepository.save(any(EmployeeType.class))).willAnswer(invocation -> invocation.getArgument(0));

        EmployeeTypeResponse response = employeeTypeService.create(request);

        assertThat(response.name()).isEqualTo("정규직");
        verify(employeeTypeRepository).save(any(EmployeeType.class));
    }

    @Test
    @DisplayName("이미 등록된 사원구분 이름이면 예외가 발생한다")
    void throwExceptionWhenEmployeeTypeNameIsDuplicated() {
        EmployeeTypeCreateRequest request = new EmployeeTypeCreateRequest("정규직");
        given(employeeTypeRepository.existsByName("정규직")).willReturn(true);

        assertThatThrownBy(() -> employeeTypeService.create(request))
                .isInstanceOf(EmployeeTypeException.class)
                .hasMessageContaining("이미 등록된 사원구분입니다.");
    }
}
