package com.example.studyfactory.domain.room.service;

import com.example.studyfactory.domain.member.dto.MemberResponse;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.member.service.ManagerAccessPolicy;
import com.example.studyfactory.domain.room.dto.SeatAssignmentUpdateRequest;
import com.example.studyfactory.domain.room.entity.SeatType;
import com.example.studyfactory.domain.room.exception.SeatException;
import com.example.studyfactory.domain.room.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final MemberRepository memberRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public MemberResponse updateAssignment(Long currentMemberId, Long memberId, SeatAssignmentUpdateRequest request) {
        Member currentMember = findMember(currentMemberId);
        ManagerAccessPolicy.validateManager(currentMember);
        Member member = findMember(memberId);
        ManagerAccessPolicy.validateMemberTarget(currentMember, member);
        validateAssignment(member.getId(), member.getBranchId(), request.seatNumber());
        member.updateSeat(request.seatNumber());

        return MemberResponse.from(member);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    /**
     * Locks the canonical layout row until the caller's write transaction
     * commits, serializing every assignment attempt for the same physical seat.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void validateAssignment(Long memberId, Long branchId, Integer seatNumber) {
        if (seatNumber == null) {
            return;
        }

        seatRepository.findByBranchIdAndNumberAndTypeForUpdate(branchId, seatNumber, SeatType.SEAT)
                .orElseThrow(SeatException::invalidSeat);

        boolean alreadyAssigned = memberId == null
                ? memberRepository.existsAssignedSeat(branchId, seatNumber)
                : memberRepository.existsAssignedSeat(branchId, seatNumber, memberId);
        if (alreadyAssigned) {
            throw SeatException.alreadyAssigned();
        }
    }
}
