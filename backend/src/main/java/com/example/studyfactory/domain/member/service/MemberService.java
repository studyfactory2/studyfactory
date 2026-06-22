package com.example.studyfactory.domain.member.service;

import com.example.studyfactory.domain.beverage.dto.BeveragePreferenceResponse;
import com.example.studyfactory.domain.beverage.entity.BeveragePreference;
import com.example.studyfactory.domain.beverage.repository.BeveragePreferenceRepository;
import com.example.studyfactory.domain.member.dto.DrinkRequest;
import com.example.studyfactory.domain.member.dto.MemberResponse;
import com.example.studyfactory.domain.member.dto.MemberSignupRequest;
import com.example.studyfactory.domain.member.dto.MemberSignupResponse;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyRequest;
import com.example.studyfactory.domain.member.dto.PreRegistrationVerifyResponse;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final BeveragePreferenceRepository beveragePreferenceRepository;

    @Transactional(readOnly = true)
    public List<MemberResponse> findAll(String name, Long branchId) {
        String searchName = toSearchName(name);

        return memberRepository.search(searchName, branchId, Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(MemberResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PreRegistrationVerifyResponse verifyPreRegistration(PreRegistrationVerifyRequest request) {
        Member member = findPreRegisteredMember(request.name().trim(), request.branchId());
        BeveragePreference beveragePreference = findLatestBeveragePreference(member);

        return PreRegistrationVerifyResponse.from(member, beveragePreference);
    }

    @Transactional
    public MemberSignupResponse signup(MemberSignupRequest request) {
        Member member = findPreRegisteredMember(request.name().trim(), request.branchId());
        validateNotSignedUp(member);
        member.signup(request.password());

        return MemberSignupResponse.from(member);
    }

    @Transactional
    public BeveragePreferenceResponse updateDrink(Long memberId, DrinkRequest request) {
        Member member = findMember(memberId);
        BeveragePreference beveragePreference = beveragePreferenceRepository
                .findFirstByMemberIdOrderByCreatedAtDesc(memberId)
                .orElseGet(() -> new BeveragePreference(member.getId(), member.getBranchId(), request.drinkSetting(), request.drinkNote()));
        beveragePreference.update(request.drinkSetting(), request.drinkNote());

        return BeveragePreferenceResponse.from(beveragePreferenceRepository.save(beveragePreference));
    }

    @Transactional
    public BeveragePreferenceResponse addDrink(Long memberId, DrinkRequest request) {
        Member member = findMember(memberId);
        BeveragePreference beveragePreference = addDrink(member, request);

        return BeveragePreferenceResponse.from(beveragePreferenceRepository.save(beveragePreference));
    }

    @Transactional
    public BeveragePreferenceResponse addDrinkForMember(Long currentMemberId, Long targetMemberId, DrinkRequest request) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        Member targetMember = findMember(targetMemberId);
        BeveragePreference beveragePreference = addDrink(targetMember, request);

        return BeveragePreferenceResponse.from(beveragePreferenceRepository.save(beveragePreference));
    }

    private BeveragePreference addDrink(Member member, DrinkRequest request) {
        BeveragePreference beveragePreference = beveragePreferenceRepository
                .findFirstByMemberIdOrderByCreatedAtDesc(member.getId())
                .orElseGet(() -> new BeveragePreference(member.getId(), member.getBranchId(), "", request.drinkNote()));
        beveragePreference.addDrinks(request.drinkSetting(), request.drinkNote());

        return beveragePreference;
    }

    @Transactional
    public void deleteDrink(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw MemberException.memberNotFound();
        }
        beveragePreferenceRepository.deleteAll(beveragePreferenceRepository.findByMemberId(memberId));
    }

    private Member findPreRegisteredMember(String name, Long branchId) {
        Member member = memberRepository.findByNameAndBranchId(name, branchId)
                .orElseThrow(MemberException::preRegistrationNotFound);
        if (member.getPassword() != null) {
            throw MemberException.alreadySignedUp();
        }

        return member;
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private BeveragePreference findLatestBeveragePreference(Member member) {
        return beveragePreferenceRepository.findFirstByMemberIdOrderByCreatedAtDesc(member.getId())
                .orElseGet(() -> new BeveragePreference(member.getId(), member.getBranchId(), "", null));
    }

    private String toSearchName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        return name.trim();
    }

    private void validateNotSignedUp(Member member) {
        if (member.getPassword() != null) {
            throw MemberException.alreadySignedUp();
        }
    }

    private void validateAllPermissions(Member member) {
        if (!member.hasAllPermissions()) {
            throw MemberException.forbidden();
        }
    }
}
