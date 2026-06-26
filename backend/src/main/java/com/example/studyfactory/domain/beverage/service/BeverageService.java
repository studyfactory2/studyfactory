package com.example.studyfactory.domain.beverage.service;

import com.example.studyfactory.domain.beverage.dto.BeveragePreferenceResponse;
import com.example.studyfactory.domain.beverage.dto.BeverageRequest;
import com.example.studyfactory.domain.beverage.dto.BeverageUpdateRequest;
import com.example.studyfactory.domain.beverage.dto.MemberBeverageResponse;
import com.example.studyfactory.domain.beverage.entity.BeveragePreference;
import com.example.studyfactory.domain.beverage.exception.BeverageException;
import com.example.studyfactory.domain.beverage.repository.BeveragePreferenceRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BeverageService {

    private final MemberRepository memberRepository;
    private final BeveragePreferenceRepository beveragePreferenceRepository;

    @Transactional(readOnly = true)
    public List<MemberBeverageResponse> findMemberBeverages(Long currentMemberId, String name, Long branchId) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        String searchName = toSearchName(name);

        return findMembers(searchName, branchId)
                .stream()
                .map(member -> MemberBeverageResponse.from(member, findLatestPreference(member)))
                .toList();
    }

    @Transactional(readOnly = true)
    public BeveragePreferenceResponse findMyDrink(Long memberId) {
        Member member = findMember(memberId);

        return BeveragePreferenceResponse.from(findLatestPreference(member));
    }

    @Transactional
    public BeveragePreferenceResponse updateDrink(Long memberId, BeverageRequest request) {
        Member member = findMember(memberId);
        BeveragePreference beveragePreference = findOrCreatePreference(member);
        beveragePreference.update(request.drinkSetting(), request.drinkNote());

        return BeveragePreferenceResponse.from(beveragePreferenceRepository.save(beveragePreference));
    }

    @Transactional
    public BeveragePreference createPreference(Long memberId, Long branchId, String drinks, String notes) {
        return beveragePreferenceRepository.save(new BeveragePreference(memberId, branchId, drinks, notes));
    }

    @Transactional
    public BeveragePreference updatePreference(Member member, String drinks, String notes) {
        BeveragePreference beveragePreference = findOrCreatePreference(member);
        beveragePreference.update(drinks, notes);

        return beveragePreference;
    }

    @Transactional
    public BeveragePreferenceResponse addDrink(Long memberId, BeverageRequest request) {
        Member member = findMember(memberId);
        BeveragePreference beveragePreference = addDrink(member, request);

        return BeveragePreferenceResponse.from(beveragePreferenceRepository.save(beveragePreference));
    }

    @Transactional
    public BeveragePreferenceResponse addDrinkForMember(Long currentMemberId, Long targetMemberId, BeverageRequest request) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        Member targetMember = findMember(targetMemberId);
        BeveragePreference beveragePreference = addDrink(targetMember, request);

        return BeveragePreferenceResponse.from(beveragePreferenceRepository.save(beveragePreference));
    }

    @Transactional
    public BeveragePreferenceResponse updateDrinkForMember(Long currentMemberId, Long targetMemberId, BeverageUpdateRequest request) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        Member targetMember = findMember(targetMemberId);
        BeveragePreference beveragePreference = findOrCreatePreference(targetMember);
        beveragePreference.update(toText(request.drinkSetting()), toText(request.drinkNote()));

        return BeveragePreferenceResponse.from(beveragePreferenceRepository.save(beveragePreference));
    }

    @Transactional
    public void deleteDrink(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw MemberException.memberNotFound();
        }
        deleteAllByMemberId(memberId);
    }

    @Transactional
    public void deleteAllByMemberId(Long memberId) {
        beveragePreferenceRepository.deleteAll(beveragePreferenceRepository.findByMemberId(memberId));
    }

    @Transactional
    public BeveragePreferenceResponse deleteDrinkItem(Long memberId, String drinkSetting) {
        Member member = findMember(memberId);
        BeveragePreference beveragePreference = deleteDrinkItem(member, drinkSetting);

        return BeveragePreferenceResponse.from(beveragePreferenceRepository.save(beveragePreference));
    }

    @Transactional
    public BeveragePreferenceResponse deleteDrinkItemForMember(Long currentMemberId, Long targetMemberId, String drinkSetting) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        Member targetMember = findMember(targetMemberId);
        BeveragePreference beveragePreference = deleteDrinkItem(targetMember, drinkSetting);

        return BeveragePreferenceResponse.from(beveragePreferenceRepository.save(beveragePreference));
    }

    private BeveragePreference addDrink(Member member, BeverageRequest request) {
        BeveragePreference beveragePreference = beveragePreferenceRepository
                .findFirstByMemberIdOrderByCreatedAtDesc(member.getId())
                .orElseGet(() -> new BeveragePreference(member.getId(), member.getBranchId(), "", request.drinkNote()));
        beveragePreference.addDrinks(request.drinkSetting(), request.drinkNote());

        return beveragePreference;
    }

    private BeveragePreference deleteDrinkItem(Member member, String drinkSetting) {
        BeveragePreference beveragePreference = beveragePreferenceRepository
                .findFirstByMemberIdOrderByCreatedAtDesc(member.getId())
                .orElseThrow(BeverageException::preferenceNotFound);
        if (!beveragePreference.removeDrink(drinkSetting)) {
            throw BeverageException.drinkNotFound();
        }

        return beveragePreference;
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    @Transactional(readOnly = true)
    public BeveragePreference findLatestPreference(Member member) {
        return beveragePreferenceRepository.findFirstByMemberIdOrderByCreatedAtDesc(member.getId())
                .orElseGet(() -> new BeveragePreference(member.getId(), member.getBranchId(), "", null));
    }

    private BeveragePreference findOrCreatePreference(Member member) {
        return beveragePreferenceRepository.findFirstByMemberIdOrderByCreatedAtDesc(member.getId())
                .orElseGet(() -> beveragePreferenceRepository.save(new BeveragePreference(member.getId(), member.getBranchId(), "", null)));
    }

    private void validateAllPermissions(Member member) {
        if (!member.hasAllPermissions()) {
            throw MemberException.forbidden();
        }
    }

    private String toSearchName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        return name.trim();
    }

    private String toText(String value) {
        if (value == null) {
            return "";
        }

        return value.trim();
    }

    private List<Member> findMembers(String name, Long branchId) {
        if (name != null && branchId != null) {
            return memberRepository.findByNameContainingAndReferenceInformationBranchIdOrderByIdAsc(name, branchId);
        }

        if (name != null) {
            return memberRepository.findByNameContainingOrderByIdAsc(name);
        }

        if (branchId != null) {
            return memberRepository.findByReferenceInformationBranchIdOrderByIdAsc(branchId);
        }

        return memberRepository.findAllByOrderByIdAsc();
    }
}
