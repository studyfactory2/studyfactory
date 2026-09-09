package com.example.studyfactory.domain.attendance.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.attendance.entity.AttendanceStatusType;
import com.example.studyfactory.domain.attendance.repository.AttendanceStatusTypeRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Isolated("애플리케이션 시작 시 공용 출석 상태 초기화를 검증한다")
@DisplayName("출석 상태 초기화 테스트")
class AttendanceStatusTypeDataInitializerTest {

    @Autowired
    private AttendanceStatusTypeDataInitializer initializer;

    @Autowired
    private AttendanceStatusTypeRepository attendanceStatusTypeRepository;

    @Test
    @DisplayName("출석 상태를 시작 전에 준비하고 재실행해도 중복하지 않는다")
    void seedsBuiltInStatusesIdempotently() {
        initializer.run(new DefaultApplicationArguments(new String[0]));

        List<AttendanceStatusType> statuses = attendanceStatusTypeRepository.findAll();
        assertThat(statuses).extracting(AttendanceStatusType::getName)
                .contains("출석");
        assertThat(statuses).filteredOn(status -> status.getName().equals("출석")).hasSize(1);
    }
}
