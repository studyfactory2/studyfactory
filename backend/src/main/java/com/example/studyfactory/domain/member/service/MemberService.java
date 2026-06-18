package com.example.studyfactory.domain.member.service;

import com.example.studyfactory.domain.member.dto.MemberSignupRequest;
import com.example.studyfactory.domain.member.dto.MemberSignupResponse;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyResponse;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.preRegistration.entity.PreRegistration;
import com.example.studyfactory.domain.preRegistration.repository.PreRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final PreRegistrationRepository preRegistrationRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public PreRegistrationVerifyResponse verifyPreRegistration(PreRegistrationVerifyRequest request) {
        PreRegistration preRegistration = findPreRegistration(request.name().trim(), request.branchId());

        return PreRegistrationVerifyResponse.from(preRegistration);
    }

    @Transactional
    public MemberSignupResponse signup(MemberSignupRequest request) {
        PreRegistration preRegistration = preRegistrationRepository.getOrThrow(request.preRegistrationId());
        validateNotSignedUp(preRegistration, request.password());

        Member member = new Member(
                preRegistration.getReferenceInformation().getBranchId(),
                preRegistration.getReferenceInformation().getEmployeeTypeId(),
                preRegistration.getName(),
                request.password(),
                preRegistration.getSeatNumber(),
                preRegistration.getExpectedJoinDate(),
                preRegistration.getReferenceInformation().getNameplateContentId(),
                preRegistration.getSubInformation().getDrinkSetting(),
                preRegistration.getSubInformation().getDrinkNote(),
                preRegistration.getSubInformation().getMemberNote()
        );

        return MemberSignupResponse.from(memberRepository.save(member));
    }

    private PreRegistration findPreRegistration(String name, Long branchId) {
        return preRegistrationRepository.findByNameAndBranchId(name, branchId)
                .orElseThrow(MemberException::preRegistrationNotFound);
    }

    private void validateNotSignedUp(PreRegistration preRegistration, String password) {
        if (memberRepository.existsByNameAndBranchIdAndPassword(
                preRegistration.getName(),
                preRegistration.getReferenceInformation().getBranchId(),
                password
        )) {
            throw MemberException.alreadySignedUp();
        }
    }
}
