package com.example.studyfactory.domain.room.service;

import com.example.studyfactory.domain.member.dto.MemberResponse;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.room.dto.SeatAssignmentUpdateRequest;
import com.example.studyfactory.domain.room.exception.SeatException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final MemberRepository memberRepository;

    @Transactional
    public MemberResponse updateAssignment(Long currentMemberId, Long memberId, SeatAssignmentUpdateRequest request) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        Member member = findMember(memberId);
        validateSeatAssignable(member, request.seatNumber());
        member.updateSeat(request.seatNumber());

        return MemberResponse.from(member);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private void validateAllPermissions(Member member) {
        if (!member.hasAllPermissions()) {
            throw MemberException.forbidden();
        }
    }

    private void validateSeatAssignable(Member member, Integer seatNumber) {
        if (seatNumber == null) {
            return;
        }

        if (memberRepository.existsAssignedSeat(member.getBranchId(), seatNumber, member.getId())) {
            throw SeatException.alreadyAssigned();
        }
    }
}
