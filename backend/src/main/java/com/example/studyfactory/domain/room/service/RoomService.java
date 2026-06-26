package com.example.studyfactory.domain.room.service;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.room.dto.RoomLayoutItemResponse;
import com.example.studyfactory.domain.room.dto.RoomLayoutResponse;
import com.example.studyfactory.domain.room.entity.Room;
import com.example.studyfactory.domain.room.repository.RoomRepository;
import com.example.studyfactory.domain.room.repository.SeatRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final MemberRepository memberRepository;
    private final RoomRepository roomRepository;
    private final SeatRepository seatRepository;

    @Transactional(readOnly = true)
    public List<RoomLayoutResponse> findLayouts(Long currentMemberId, Long branchId) {
        Member currentMember = memberRepository.findById(currentMemberId).orElseThrow(MemberException::memberNotFound);
        validateAllPermissions(currentMember);
        Long targetBranchId = resolveBranchId(currentMember, branchId);

        return roomRepository.findByBranchIdOrderByIdAsc(targetBranchId).stream()
                .map(this::toResponse)
                .toList();
    }

    private RoomLayoutResponse toResponse(Room room) {
        List<RoomLayoutItemResponse> items = seatRepository.findByRoomIdOrderByGridRowAscGridColAsc(room.getId()).stream()
                .map(RoomLayoutItemResponse::from)
                .toList();

        return RoomLayoutResponse.from(room, items);
    }

    private void validateAllPermissions(Member member) {
        if (!member.hasAllPermissions()) {
            throw MemberException.forbidden();
        }
    }

    private Long resolveBranchId(Member currentMember, Long branchId) {
        if (branchId == null) {
            return currentMember.getBranchId();
        }

        return branchId;
    }
}
