package com.example.studyfactory.domain.dailyTask.service;

import com.example.studyfactory.domain.dailyTask.dto.DailyTaskCalendarDayResponse;
import com.example.studyfactory.domain.dailyTask.dto.DailyTaskCalendarResponse;
import com.example.studyfactory.domain.dailyTask.dto.DailyTaskCreateRequest;
import com.example.studyfactory.domain.dailyTask.dto.DailyTaskDayResponse;
import com.example.studyfactory.domain.dailyTask.dto.DailyTaskItemResponse;
import com.example.studyfactory.domain.dailyTask.dto.DailyTaskUpdateRequest;
import com.example.studyfactory.domain.dailyTask.entity.DailyTaskDay;
import com.example.studyfactory.domain.dailyTask.entity.DailyTaskItem;
import com.example.studyfactory.domain.dailyTask.exception.DailyTaskException;
import com.example.studyfactory.domain.dailyTask.repository.DailyTaskDayRepository;
import com.example.studyfactory.domain.dailyTask.repository.DailyTaskItemRepository;
import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Personal, date-based tasks. Never reads or writes plans, branch todos, or study records. */
@Service
@RequiredArgsConstructor
public class DailyTaskService {

    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final long MAX_CLIENT_REVISION = 9_007_199_254_740_991L;

    private final MemberRepository memberRepository;
    private final DailyTaskDayRepository dayRepository;
    private final DailyTaskItemRepository itemRepository;

    // Keep the revision and its items in one consistent snapshot without creating rows on read.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DailyTaskDayResponse findMine(Long memberId, LocalDate date) {
        requireMember(memberId, false);
        validateDate(date);
        return snapshot(memberId, date);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DailyTaskCalendarResponse findCalendarMine(Long memberId, String month) {
        requireMember(memberId, false);
        YearMonth selectedMonth = validateMonth(month);
        LocalDate start = selectedMonth.atDay(1);
        LocalDate end = selectedMonth.plusMonths(1).atDay(1);
        Map<LocalDate, DailyTaskItemRepository.DailyTaskDateCount> counts = itemRepository
                .calendarCounts(memberId, start, end).stream()
                .collect(Collectors.toMap(DailyTaskItemRepository.DailyTaskDateCount::getDate, Function.identity()));
        return new DailyTaskCalendarResponse(memberId, selectedMonth.toString(), start.datesUntil(end).map(date -> {
            var count = counts.get(date);
            return new DailyTaskCalendarDayResponse(date,
                    count == null ? 0 : count.getTotalCount(), count == null ? 0 : count.getCompletedCount());
        }).toList());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DailyTaskDayResponse createMine(Long memberId, DailyTaskCreateRequest request) {
        if (request == null || request.clientRequestId() == null) {
            throw DailyTaskException.invalidRequest("할 일과 저장 요청 번호를 입력해 주세요.");
        }
        validateDate(request.date());
        validateRevision(request.expectedRevision());
        String content = validateContent(request.content());
        // The same lock is held by account deletion, preventing orphan tasks after deletion.
        requireMember(memberId, true);
        String fingerprint = fingerprint(request.date() + "\n" + content);
        DailyTaskItem previous = itemRepository
                .findByMemberIdAndClientRequestId(memberId, request.clientRequestId()).orElse(null);
        if (previous != null) {
            if (!previous.getCreateFingerprint().equals(fingerprint)) {
                throw DailyTaskException.requestIdReused();
            }
            // Replays return current state, not a cached original response or a resurrected task.
            return snapshot(memberId, request.date());
        }
        DailyTaskDay day = checkedDay(memberId, request.date(), request.expectedRevision());
        Integer lastOrder = itemRepository.findMaxSortOrder(memberId, request.date());
        if (lastOrder != null && lastOrder == Integer.MAX_VALUE) {
            throw DailyTaskException.invalidRequest("이 날짜에 할 일을 더 추가할 수 없습니다.");
        }
        itemRepository.save(new DailyTaskItem(memberId, request.date(), content,
                lastOrder == null ? 0 : lastOrder + 1, request.clientRequestId(), fingerprint));
        advance(day);
        return snapshot(memberId, request.date());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DailyTaskDayResponse updateMine(Long memberId, Long itemId, DailyTaskUpdateRequest request) {
        if (request == null || (request.content() == null && request.completed() == null)) {
            throw DailyTaskException.invalidRequest("변경할 내용이나 완료 상태를 입력해 주세요.");
        }
        validateDate(request.date());
        validateRevision(request.expectedRevision());
        validateItemId(itemId);
        String content = request.content() == null ? null : validateContent(request.content());
        requireMember(memberId, true);
        DailyTaskDay day = checkedDay(memberId, request.date(), request.expectedRevision());
        findItem(memberId, request.date(), itemId).update(content, request.completed());
        advance(day);
        return snapshot(memberId, request.date());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DailyTaskDayResponse deleteMine(Long memberId, Long itemId, LocalDate date, Long expectedRevision) {
        validateDate(date);
        validateRevision(expectedRevision);
        validateItemId(itemId);
        requireMember(memberId, true);
        DailyTaskDay day = checkedDay(memberId, date, expectedRevision);
        // Retain the create token/fingerprint so a delayed POST cannot recreate a deleted task.
        findItem(memberId, date, itemId).markDeleted();
        advance(day);
        return snapshot(memberId, date);
    }

    private Member requireMember(Long memberId, boolean forUpdate) {
        Member member = (forUpdate ? memberRepository.findByIdForUpdate(memberId) : memberRepository.findById(memberId))
                .orElseThrow(MemberException::memberNotFound);
        if (member.getRole() != MemberRole.MEMBER) {
            throw MemberException.forbidden();
        }
        return member;
    }

    private DailyTaskItem findItem(Long memberId, LocalDate date, Long itemId) {
        return itemRepository.findByIdAndMemberIdAndDate(itemId, memberId, date)
                .filter(item -> !item.isDeleted()).orElseThrow(DailyTaskException::itemNotFound);
    }

    private DailyTaskDay checkedDay(Long memberId, LocalDate date, long expectedRevision) {
        DailyTaskDay day = dayRepository.findByMemberIdAndDate(memberId, date)
                .orElseGet(() -> new DailyTaskDay(memberId, date));
        if (day.getRevision() != expectedRevision) {
            throw DailyTaskException.staleRevision();
        }
        if (day.getRevision() >= MAX_CLIENT_REVISION) {
            throw DailyTaskException.invalidRequest("이 날짜의 저장 버전 한도에 도달했습니다. 관리자에게 문의해 주세요.");
        }
        return day;
    }

    private void advance(DailyTaskDay day) {
        day.advance();
        dayRepository.save(day);
    }

    private DailyTaskDayResponse snapshot(Long memberId, LocalDate date) {
        long revision = dayRepository.findByMemberIdAndDate(memberId, date)
                .map(DailyTaskDay::getRevision).orElse(0L);
        return new DailyTaskDayResponse(memberId, date, revision, itemRepository
                .findByMemberIdAndDateAndDeletedFalseOrderBySortOrderAscIdAsc(memberId, date).stream()
                .map(item -> new DailyTaskItemResponse(item.getId(), item.getContent(), item.isCompleted(), item.getSortOrder()))
                .toList());
    }

    private void validateDate(LocalDate date) {
        if (date == null || date.getYear() < 1 || date.getYear() > 9999) {
            throw DailyTaskException.invalidRequest("할 일을 기록할 날짜를 YYYY-MM-DD 형식으로 입력해 주세요.");
        }
    }

    private YearMonth validateMonth(String month) {
        if (month == null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw DailyTaskException.invalidRequest("조회할 달을 YYYY-MM 형식으로 입력해 주세요.");
        }
        try {
            YearMonth parsed = YearMonth.parse(month);
            validateDate(parsed.atDay(1));
            return parsed;
        } catch (DateTimeException exception) {
            throw DailyTaskException.invalidRequest("조회할 달을 확인해 주세요.");
        }
    }

    private void validateRevision(Long revision) {
        if (revision == null || revision < 0 || revision > MAX_CLIENT_REVISION) {
            throw DailyTaskException.invalidRequest("조회한 날짜의 저장 버전이 필요합니다. 최신 내용을 확인해 주세요.");
        }
    }

    private void validateItemId(Long itemId) {
        if (itemId == null || itemId <= 0) {
            throw DailyTaskException.invalidRequest("할 일 항목을 확인해 주세요.");
        }
    }

    private String validateContent(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank() || normalized.length() > MAX_CONTENT_LENGTH) {
            throw DailyTaskException.invalidRequest("할 일은 빈 내용 없이 2,000자 이내로 입력해 주세요.");
        }
        return normalized;
    }

    private String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
