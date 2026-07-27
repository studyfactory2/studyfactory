package com.example.studyfactory.domain.beverage.service;

import com.example.studyfactory.domain.beverage.dto.BeveragePreferenceResponse;
import com.example.studyfactory.domain.beverage.dto.BeverageItemRequest;
import com.example.studyfactory.domain.beverage.dto.BeverageRequest;
import com.example.studyfactory.domain.beverage.dto.BeverageUpdateRequest;
import com.example.studyfactory.domain.beverage.dto.MemberBeverageResponse;
import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import com.example.studyfactory.domain.beverage.exception.BeverageException;
import com.example.studyfactory.domain.beverage.repository.BeverageItemRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BeverageService {

    private final MemberRepository memberRepository;
    private final BeverageItemRepository beverageItemRepository;

    @Transactional(readOnly = true)
    public List<MemberBeverageResponse> findMemberBeverages(Long currentMemberId, String name, Long branchId) {
        Member currentMember = findMember(currentMemberId);
        validateAllPermissions(currentMember);
        return findMembers(toSearchName(name), branchId).stream()
                .map(member -> MemberBeverageResponse.from(member, findItems(member.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BeveragePreferenceResponse findMyDrink(Long memberId) {
        Member member = findMember(memberId);
        return responseOf(member, findItems(memberId));
    }

    @Transactional
    public BeveragePreferenceResponse updateDrink(Long memberId, BeverageRequest request) {
        Member member = findMember(memberId);
        replaceItems(memberId, request.items(), request.drinkSetting(), resolveNotes(request.drinkSetting(), request.drinkNotes(), request.drinkNote()));
        return responseOf(member, findItems(memberId));
    }

    @Transactional
    public List<BeverageItem> createPreference(Long memberId, String drinks, String note) {
        return replaceItems(memberId, drinks, resolveNotes(drinks, null, note));
    }

    @Transactional
    public List<BeverageItem> createPreference(Long memberId, String drinks, Map<String, String> drinkNotes) {
        return replaceItems(memberId, drinks, resolveNotes(drinks, drinkNotes, null));
    }

    @Transactional
    public List<BeverageItem> updatePreference(Member member, String drinks, String note) {
        return replaceItems(member.getId(), drinks, resolveNotes(drinks, null, note));
    }

    @Transactional
    public List<BeverageItem> updatePreference(Member member, String drinks, Map<String, String> drinkNotes) {
        return replaceItems(member.getId(), drinks, resolveNotes(drinks, drinkNotes, null));
    }

    @Transactional
    public BeveragePreferenceResponse addDrink(Long memberId, BeverageRequest request) {
        Member member = findMember(memberId);
        addItems(memberId, request.items(), request.drinkSetting(), resolveNotes(request.drinkSetting(), request.drinkNotes(), request.drinkNote()));
        return responseOf(member, findItems(memberId));
    }

    @Transactional
    public BeveragePreferenceResponse addDrinkForMember(Long currentMemberId, Long targetMemberId, BeverageRequest request) {
        validateAllPermissions(findMember(currentMemberId));
        Member targetMember = findMember(targetMemberId);
        addItems(targetMemberId, request.items(), request.drinkSetting(), resolveNotes(request.drinkSetting(), request.drinkNotes(), request.drinkNote()));
        return responseOf(targetMember, findItems(targetMemberId));
    }

    @Transactional
    public BeveragePreferenceResponse updateDrinkForMember(Long currentMemberId, Long targetMemberId, BeverageUpdateRequest request) {
        validateAllPermissions(findMember(currentMemberId));
        Member targetMember = findMember(targetMemberId);
        replaceItems(targetMemberId, request.items(), request.drinkSetting(), resolveNotes(request.drinkSetting(), request.drinkNotes(), request.drinkNote()));
        return responseOf(targetMember, findItems(targetMemberId));
    }

    @Transactional
    public void deleteDrink(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw MemberException.memberNotFound();
        }
        beverageItemRepository.deleteByMemberId(memberId);
    }

    @Transactional
    public void deleteAllByMemberId(Long memberId) {
        beverageItemRepository.deleteByMemberId(memberId);
    }

    @Transactional
    public BeveragePreferenceResponse deleteDrinkItem(Long memberId, String drinkSetting) {
        Member member = findMember(memberId);
        deleteItem(memberId, drinkSetting);
        return responseOf(member, findItems(memberId));
    }

    @Transactional
    public BeveragePreferenceResponse deleteDrinkItemForMember(Long currentMemberId, Long targetMemberId, String drinkSetting) {
        validateAllPermissions(findMember(currentMemberId));
        Member targetMember = findMember(targetMemberId);
        deleteItem(targetMemberId, drinkSetting);
        return responseOf(targetMember, findItems(targetMemberId));
    }

    @Transactional(readOnly = true)
    public List<BeverageItem> findItems(Long memberId) {
        return beverageItemRepository.findByMemberIdOrderByCreatedAtAsc(memberId);
    }

    private List<BeverageItem> replaceItems(Long memberId, String drinks, Map<String, String> drinkNotes) {
        return replaceItems(memberId, null, drinks, drinkNotes);
    }

    private List<BeverageItem> replaceItems(Long memberId, List<BeverageItemRequest> requestedItems, String drinks, Map<String, String> drinkNotes) {
        beverageItemRepository.deleteByMemberId(memberId);
        return beverageItemRepository.saveAll(toItems(memberId, requestedItems, drinks, drinkNotes));
    }

    private void addItems(Long memberId, List<BeverageItemRequest> requestedItems, String drinks, Map<String, String> drinkNotes) {
        List<BeverageItem> additions = toItems(memberId, requestedItems, drinks, drinkNotes);
        beverageItemRepository.saveAll(additions);
    }

    private void deleteItem(Long memberId, String drink) {
        if (beverageItemRepository.deleteByMemberIdAndName(memberId, normalize(drink)) == 0) {
            throw BeverageException.drinkNotFound();
        }
    }

    private List<BeverageItem> toItems(Long memberId, String drinks, Map<String, String> drinkNotes) {
        return toItems(memberId, null, drinks, drinkNotes);
    }

    private List<BeverageItem> toItems(Long memberId, List<BeverageItemRequest> requestedItems, String drinks, Map<String, String> drinkNotes) {
        if (requestedItems != null) {
            return requestedItems.stream()
                    .filter(item -> item != null)
                    .map(item -> new BeverageItem(memberId, normalize(item.name()), item.note()))
                    .filter(item -> !item.getName().isBlank())
                    .toList();
        }
        return toDrinks(drinks).stream()
                .map(drink -> new BeverageItem(memberId, drink, drinkNotes.get(drink)))
                .toList();
    }

    private BeveragePreferenceResponse responseOf(Member member, List<BeverageItem> items) {
        return BeveragePreferenceResponse.from(member, items);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private void validateAllPermissions(Member member) {
        if (!member.hasAllPermissions()) {
            throw MemberException.forbidden();
        }
    }

    private Map<String, String> resolveNotes(String drinks, Map<String, String> requestedNotes, String legacyNote) {
        Map<String, String> notes = new LinkedHashMap<>();
        if (requestedNotes != null) {
            requestedNotes.forEach((drink, note) -> {
                String normalizedDrink = normalize(drink);
                if (!normalizedDrink.isBlank() && note != null && !note.isBlank()) {
                    notes.put(normalizedDrink, note.trim());
                }
            });
        }
        if (!notes.isEmpty() || legacyNote == null || legacyNote.isBlank()) {
            return notes;
        }
        toDrinks(drinks).forEach(drink -> notes.put(drink, legacyNote.trim()));
        return notes;
    }

    private List<String> toDrinks(String drinks) {
        if (drinks == null || drinks.isBlank()) {
            return List.of();
        }
        return Arrays.stream(drinks.split("\\R|,"))
                .map(this::normalize)
                .filter(drink -> !drink.isBlank())
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String toSearchName(String name) {
        return name == null || name.isBlank() ? null : name.trim();
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
