package com.example.studyfactory.domain.beverage.service;

import com.example.studyfactory.domain.beverage.dto.BeveragePreferenceResponse;
import com.example.studyfactory.domain.beverage.dto.BeverageItemRequest;
import com.example.studyfactory.domain.beverage.dto.BeverageRequest;
import com.example.studyfactory.domain.beverage.dto.BeverageUpdateRequest;
import com.example.studyfactory.domain.beverage.dto.MemberBeverageResponse;
import com.example.studyfactory.domain.beverage.entity.BeverageItem;
import com.example.studyfactory.domain.beverage.entity.BeveragePreferenceAudit;
import com.example.studyfactory.domain.beverage.exception.BeverageException;
import com.example.studyfactory.domain.beverage.repository.BeverageItemRepository;
import com.example.studyfactory.domain.beverage.repository.BeveragePreferenceAuditRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.member.service.ManagerAccessPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
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
    private final BeveragePreferenceAuditRepository beveragePreferenceAuditRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<MemberBeverageResponse> findMemberBeverages(Long currentMemberId, String name, Long branchId) {
        Member currentMember = findMember(currentMemberId);
        Long targetBranchId = ManagerAccessPolicy.resolveOptionalAdminBranch(currentMember, branchId);
        List<Member> members = findMembers(toSearchName(name), targetBranchId);
        Map<Long, BeveragePreferenceAudit> auditsByMemberId = findAuditsByMemberId(members);
        return members.stream()
                .map(member -> memberResponseOf(
                        member,
                        findItems(member.getId()),
                        auditsByMemberId.get(member.getId())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public BeveragePreferenceResponse findMyDrink(Long memberId) {
        Member member = findMember(memberId);
        return responseOf(member, findItems(memberId));
    }

    @Transactional
    public BeveragePreferenceResponse updateDrink(Long memberId, BeverageRequest request) {
        Member member = findMemberForUpdate(memberId);
        replaceItems(memberId, request.items(), request.drinkSetting(), resolveNotes(request.drinkSetting(), request.drinkNotes(), request.drinkNote()));
        return responseOf(member, findItems(memberId));
    }

    @Transactional
    public List<BeverageItem> createPreference(Long memberId, String drinks, String note) {
        findMemberForUpdate(memberId);
        return replaceItems(memberId, drinks, resolveNotes(drinks, null, note));
    }

    @Transactional
    public List<BeverageItem> createPreference(Long memberId, String drinks, Map<String, String> drinkNotes) {
        findMemberForUpdate(memberId);
        return replaceItems(memberId, drinks, resolveNotes(drinks, drinkNotes, null));
    }

    @Transactional
    public List<BeverageItem> updatePreference(Member member, String drinks, String note) {
        findMemberForUpdate(member.getId());
        return replaceItems(member.getId(), drinks, resolveNotes(drinks, null, note));
    }

    @Transactional
    public List<BeverageItem> updatePreference(Member member, String drinks, Map<String, String> drinkNotes) {
        findMemberForUpdate(member.getId());
        return replaceItems(member.getId(), drinks, resolveNotes(drinks, drinkNotes, null));
    }

    @Transactional
    public BeveragePreferenceResponse addDrink(Long memberId, BeverageRequest request) {
        Member member = findMemberForUpdate(memberId);
        addItems(memberId, request.items(), request.drinkSetting(), resolveNotes(request.drinkSetting(), request.drinkNotes(), request.drinkNote()));
        return responseOf(member, findItems(memberId));
    }

    @Transactional
    public BeveragePreferenceResponse addDrinkForMember(Long currentMemberId, Long targetMemberId, BeverageRequest request) {
        Member currentMember = findMember(currentMemberId);
        ManagerAccessPolicy.validateManager(currentMember);
        Member targetMember = findMemberForUpdate(targetMemberId);
        ManagerAccessPolicy.validateManagerTarget(currentMember, targetMember);
        addItems(targetMemberId, request.items(), request.drinkSetting(), resolveNotes(request.drinkSetting(), request.drinkNotes(), request.drinkNote()));
        return responseOf(targetMember, findItems(targetMemberId));
    }

    @Transactional
    public BeveragePreferenceResponse updateDrinkForMember(Long currentMemberId, Long targetMemberId, BeverageUpdateRequest request) {
        Member currentMember = findMember(currentMemberId);
        ManagerAccessPolicy.validateManager(currentMember);
        Member targetMember = findMemberForUpdate(targetMemberId);
        ManagerAccessPolicy.validateManagerTarget(currentMember, targetMember);
        replaceItems(targetMemberId, request.items(), request.drinkSetting(), resolveNotes(request.drinkSetting(), request.drinkNotes(), request.drinkNote()));
        return responseOf(targetMember, findItems(targetMemberId));
    }

    @Transactional
    public void deleteDrink(Long memberId) {
        findMemberForUpdate(memberId);
        recordPreferenceChange(memberId);
        beverageItemRepository.deleteByMemberId(memberId);
    }

    @Transactional
    public void deleteAllByMemberId(Long memberId) {
        findMemberForUpdate(memberId);
        beverageItemRepository.deleteByMemberId(memberId);
        beveragePreferenceAuditRepository.deleteByMemberId(memberId);
    }

    @Transactional
    public BeveragePreferenceResponse deleteDrinkItem(Long memberId, String drinkSetting) {
        Member member = findMemberForUpdate(memberId);
        deleteItem(memberId, drinkSetting);
        return responseOf(member, findItems(memberId));
    }

    @Transactional
    public BeveragePreferenceResponse deleteDrinkItemForMember(Long currentMemberId, Long targetMemberId, String drinkSetting) {
        Member currentMember = findMember(currentMemberId);
        ManagerAccessPolicy.validateManager(currentMember);
        Member targetMember = findMemberForUpdate(targetMemberId);
        ManagerAccessPolicy.validateManagerTarget(currentMember, targetMember);
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
        recordPreferenceChange(memberId);
        beverageItemRepository.deleteByMemberId(memberId);
        return beverageItemRepository.saveAll(toItems(memberId, requestedItems, drinks, drinkNotes));
    }

    private void addItems(Long memberId, List<BeverageItemRequest> requestedItems, String drinks, Map<String, String> drinkNotes) {
        recordPreferenceChange(memberId);
        List<BeverageItem> additions = toItems(memberId, requestedItems, drinks, drinkNotes);
        beverageItemRepository.saveAll(additions);
    }

    private void deleteItem(Long memberId, String drink) {
        recordPreferenceChange(memberId);
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
        PreferenceTimestamps timestamps = resolveTimestamps(
                items,
                beveragePreferenceAuditRepository.findByMemberId(member.getId()).orElse(null)
        );
        return BeveragePreferenceResponse.from(member, items, timestamps.createdAt(), timestamps.updatedAt());
    }

    private MemberBeverageResponse memberResponseOf(
            Member member,
            List<BeverageItem> items,
            BeveragePreferenceAudit audit
    ) {
        PreferenceTimestamps timestamps = resolveTimestamps(items, audit);
        return MemberBeverageResponse.from(member, items, timestamps.createdAt(), timestamps.updatedAt());
    }

    private Map<Long, BeveragePreferenceAudit> findAuditsByMemberId(List<Member> members) {
        if (members.isEmpty()) {
            return Map.of();
        }
        Map<Long, BeveragePreferenceAudit> result = new HashMap<>();
        beveragePreferenceAuditRepository.findByMemberIdIn(
                members.stream().map(Member::getId).toList()
        ).forEach(audit -> result.put(audit.getMemberId(), audit));
        return result;
    }

    private void recordPreferenceChange(Long memberId) {
        Instant changedAt = clock.instant();
        BeveragePreferenceAudit audit = beveragePreferenceAuditRepository.findByMemberId(memberId).orElse(null);
        if (audit == null) {
            Instant createdAt = legacyCreatedAt(findItems(memberId));
            beveragePreferenceAuditRepository.save(new BeveragePreferenceAudit(
                    memberId,
                    createdAt == null ? changedAt : createdAt,
                    changedAt
            ));
            return;
        }
        audit.touch(changedAt);
    }

    private PreferenceTimestamps resolveTimestamps(List<BeverageItem> items, BeveragePreferenceAudit audit) {
        if (audit != null) {
            return new PreferenceTimestamps(audit.getCreatedAt(), audit.getUpdatedAt());
        }
        return new PreferenceTimestamps(legacyCreatedAt(items), legacyUpdatedAt(items));
    }

    /** Existing LocalDateTime audit rows were written by the UTC production JVM. */
    private Instant legacyCreatedAt(List<BeverageItem> items) {
        return items.stream()
                .map(BeverageItem::getCreatedAt)
                .filter(java.util.Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .map(value -> value.toInstant(ZoneOffset.UTC))
                .orElse(null);
    }

    /** Existing LocalDateTime audit rows were written by the UTC production JVM. */
    private Instant legacyUpdatedAt(List<BeverageItem> items) {
        return items.stream()
                .map(BeverageItem::getUpdatedAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .map(value -> value.toInstant(ZoneOffset.UTC))
                .orElse(null);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private Member findMemberForUpdate(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId).orElseThrow(MemberException::memberNotFound);
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

    private record PreferenceTimestamps(Instant createdAt, Instant updatedAt) {
    }
}
