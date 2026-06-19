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
                new SideDishMealInformation(LocalDate.now(clock), request.mealType()),
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

    private void validateTotalPrice(SideDishCreateRequest request) {
        if (request.itemPrice() != request.totalPrice()) {
            throw SideDishException.invalidTotalPrice();
        }
    }

    private void validateDeadline(SideDishCreateRequest request) {
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

    private String toItems(SideDishCreateRequest request) {
        return request.menuName().trim() + ": " + request.itemPrice();
    }
}
