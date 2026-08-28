package com.example.studyfactory.domain.attendance.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.studyfactory.domain.attendance.entity.Attendance;
import com.example.studyfactory.domain.attendance.entity.AttendanceReferenceInformation;
import com.example.studyfactory.domain.attendance.entity.AttendanceSlotInformation;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
@DisplayName("출석 저장소 테스트")
class AttendanceRepositoryTest {

    private static final Long MEMBER_ID = 1L;
    private static final LocalDate FROM_DATE = LocalDate.of(2026, 8, 25);
    private static final LocalDate TO_DATE = LocalDate.of(2026, 8, 27);

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Test
    @DisplayName("회원과 양끝 날짜를 포함한 범위의 출석만 날짜 및 교시순으로 조회한다")
    void findByMemberIdAndAttendanceDateBetween() {
        Attendance first = attendance(MEMBER_ID, FROM_DATE, 7);
        Attendance second = attendance(MEMBER_ID, FROM_DATE.plusDays(1), 1);
        Attendance third = attendance(MEMBER_ID, FROM_DATE.plusDays(1), 3);
        Attendance fourth = attendance(MEMBER_ID, TO_DATE, 2);
        attendanceRepository.saveAllAndFlush(List.of(
                third,
                attendance(MEMBER_ID, FROM_DATE.minusDays(1), 1),
                fourth,
                attendance(2L, FROM_DATE.plusDays(1), 2),
                first,
                attendance(MEMBER_ID, TO_DATE.plusDays(1), 1),
                second
        ));

        List<Attendance> result = attendanceRepository.findByMemberIdAndAttendanceDateBetween(
                MEMBER_ID,
                FROM_DATE,
                TO_DATE
        );

        assertThat(result).containsExactly(first, second, third, fourth);
    }

    private Attendance attendance(Long memberId, LocalDate date, int slot) {
        return new Attendance(
                new AttendanceReferenceInformation(memberId, 2L, 3L, 4L),
                new AttendanceSlotInformation(date, slot, null)
        );
    }
}
