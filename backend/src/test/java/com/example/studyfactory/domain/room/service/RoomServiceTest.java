package com.example.studyfactory.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.room.dto.RoomLayoutResponse;
import com.example.studyfactory.domain.room.entity.Room;
import com.example.studyfactory.domain.room.repository.RoomRepository;
import com.example.studyfactory.domain.room.repository.SeatRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @InjectMocks
    private RoomService roomService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private SeatRepository seatRepository;

    @Test
    void staffReadsOwnBranchWhenBranchIsMissing() {
        Member staff = member(1L, MemberRole.STAFF, 2L);
        Room room = new Room(2L, "1작업실", 14, 7);
        ReflectionTestUtils.setField(room, "id", 10L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(staff));
        given(roomRepository.findByBranchIdOrderByIdAsc(2L)).willReturn(List.of(room));
        given(seatRepository.findByRoomIdOrderByGridRowAscGridColAsc(10L)).willReturn(List.of());

        List<RoomLayoutResponse> responses = roomService.findLayouts(1L, null);

        assertThat(responses).singleElement().extracting(RoomLayoutResponse::branchId).isEqualTo(2L);
        then(roomRepository).should().findByBranchIdOrderByIdAsc(2L);
    }

    @Test
    void rejectsStaffReadingAnotherBranch() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L, MemberRole.STAFF, 2L)));

        assertThatThrownBy(() -> roomService.findLayouts(1L, 3L))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    @Test
    void adminMayReadExplicitOtherBranch() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L, MemberRole.ADMIN, 2L)));
        given(roomRepository.findByBranchIdOrderByIdAsc(3L)).willReturn(List.of());

        assertThat(roomService.findLayouts(1L, 3L)).isEmpty();
        then(roomRepository).should().findByBranchIdOrderByIdAsc(3L);
    }

    @Test
    void rejectsMemberReadingRoomLayouts() {
        given(memberRepository.findById(1L)).willReturn(Optional.of(member(1L, MemberRole.MEMBER, 2L)));

        assertThatThrownBy(() -> roomService.findLayouts(1L, null))
                .isInstanceOf(MemberException.class)
                .hasMessageContaining("권한이 없습니다.");
    }

    private Member member(Long id, MemberRole role, Long branchId) {
        Member member = new Member(
                branchId,
                "operator",
                "password123",
                role,
                null,
                LocalDate.of(2026, 7, 1),
                null
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
