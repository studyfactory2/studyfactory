package com.example.studyfactory.domain.sideDish.service;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.sideDish.dto.SideDishCreateRequest;
import com.example.studyfactory.domain.sideDish.dto.SideDishResponse;
import com.example.studyfactory.domain.sideDish.entity.SideDishMealInformation;
import com.example.studyfactory.domain.sideDish.entity.SideDishOrderInformation;
import com.example.studyfactory.domain.sideDish.entity.SideDishReferenceInformation;
import com.example.studyfactory.domain.sideDish.entity.SideDishRequest;
import com.example.studyfactory.domain.sideDish.exception.SideDishException;
import com.example.studyfactory.domain.sideDish.repository.SideDishRequestRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SideDishService {

    private final SideDishRequestRepository sideDishRequestRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    @Transactional
    public SideDishResponse create(Long memberId, SideDishCreateRequest request) {
        validateTotalPrice(request);
        validateDeadline(request);
        Member member = memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
        SideDishRequest sideDishRequest = new SideDishRequest(
                new SideDishReferenceInformation(member.getId(), member.getBranchId()),
                new SideDishMealInformation(resolveMealDate(request), request.mealType()),
                new SideDishOrderInformation(toItems(request), request.totalPrice())
        );

        return SideDishResponse.from(sideDishRequestRepository.save(sideDishRequest));
    }

    @Transactional(readOnly = true)
    public List<SideDishResponse> findMineByDate(Long memberId, LocalDate date) {
        return sideDishRequestRepository.findMineByDate(memberId, date)
                .stream()
                .map(SideDishResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long memberId, Long sideDishId) {
        SideDishRequest sideDishRequest = sideDishRequestRepository.findById(sideDishId).orElseThrow(SideDishException::sideDishNotFound);
        Member member = memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
        validateOwner(member, sideDishRequest);
        sideDishRequestRepository.delete(sideDishRequest);
    }

    private void validateTotalPrice(SideDishCreateRequest request) {
        if (request.itemPrice() != request.totalPrice()) {
            throw SideDishException.invalidTotalPrice();
        }
    }

    private void validateDeadline(SideDishCreateRequest request) {
        if (!isToday(resolveMealDate(request))) {
            return;
        }

        LocalTime now = LocalTime.now(clock);
        switch (request.mealType()) {
            case LUNCH -> validateLunchDeadline(now);
            case DINNER -> validateDinnerDeadline(now);
        }
    }

    private void validateLunchDeadline(LocalTime now) {
        if (now.isAfter(LocalTime.of(10, 45))) {
            throw SideDishException.lunchDeadlineExceeded();
        }
    }

    private void validateDinnerDeadline(LocalTime now) {
        if (now.isAfter(LocalTime.of(16, 30))) {
            throw SideDishException.dinnerDeadlineExceeded();
        }
    }

    private void validateOwner(Member member, SideDishRequest sideDishRequest) {
        if (member.hasAllPermissions()) {
            return;
        }
        if (!sideDishRequest.getMemberId().equals(member.getId())) {
            throw SideDishException.notOwner();
        }
    }

    private String toItems(SideDishCreateRequest request) {
        return request.menuName().trim() + ": " + request.itemPrice();
    }

    private LocalDate resolveMealDate(SideDishCreateRequest request) {
        if (request.mealDate() == null) {
            return LocalDate.now(clock);
        }

        return request.mealDate();
    }

    private boolean isToday(LocalDate mealDate) {
        return mealDate.equals(LocalDate.now(clock));
    }
}
