package com.example.studyfactory.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.room.dto.SeatAssignmentUpdateRequest;
import com.example.studyfactory.domain.room.exception.SeatException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("좌석 서비스 테스트")
class SeatServiceTest {

    @InjectMocks
    private SeatService seatService;

    @Mock
    private MemberRepository memberRepository;

    @Test
    @DisplayName("스태프가 사원의 좌석 배정을 해제한다")
    void updateAssignmentByStaff() {
        Member staff = createMember(2L, MemberRole.STAFF);
        Member member = createMember(1L, MemberRole.MEMBER);
        given(memberRepository.findById(2L)).willReturn(Optional.of(staff));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThat(seatService.updateAssignment(2L, 1L, new SeatAssignmentUpdateRequest(null)).seatNumber()).isNull();
        assertThat(member.getSeatNumber()).isNull();
    }

    @Test
    @DisplayName("스태프가 사원에게 좌석을 배정한다")
    void assignSeatByStaff() {
        Member staff = createMember(2L, MemberRole.STAFF);
        Member member = createMember(1L, MemberRole.MEMBER);
        member.updateSeat(null);
        given(memberRepository.findById(2L)).willReturn(Optional.of(staff));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberRepository.existsAssignedSeat(1L, 39, 1L)).willReturn(false);

        assertThat(seatService.updateAssignment(2L, 1L, new SeatAssignmentUpdateRequest(39)).seatNumber()).isEqualTo(39);
        assertThat(member.getSeatNumber()).isEqualTo(39);
    }

    @Test
    @DisplayName("이미 배정된 좌석으로 변경하면 예외가 발생한다")
    void throwExceptionWhenSeatAlreadyAssigned() {
        Member staff = createMember(2L, MemberRole.STAFF);
        Member member = createMember(1L, MemberRole.MEMBER);
        given(memberRepository.findById(2L)).willReturn(Optional.of(staff));
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(memberRepository.existsAssignedSeat(1L, 20, 1L)).willReturn(true);

        assertThatThrownBy(() -> seatService.updateAssignment(2L, 1L, new SeatAssignmentUpdateRequest(20)))
                .isInstanceOf(SeatException.class)
                .hasMessageContaining("이미 배정된 좌석입니다.");
    }

    @Test
    @DisplayName("일반 사원이 좌석 배정을 변경하면 예외가 발생한다")
    void throwExceptionWhenUpdateAssignmentWithoutPermission() {
        Member member = createMember(1L, MemberRole.MEMBER);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> seatService.updateAssignment(1L, 2L, new SeatAssignmentUpdateRequest(null)))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    private Member createMember(Long id, MemberRole role) {
        Member member = new Member(1L, "hong", "password123", role, 12, LocalDate.of(2026, 7, 1), 3L, "오전 교육 예정");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
