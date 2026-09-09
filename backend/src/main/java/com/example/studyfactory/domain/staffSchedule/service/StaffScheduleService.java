package com.example.studyfactory.domain.staffSchedule.service;

import com.example.studyfactory.domain.member.entity.Member;
import com.example.studyfactory.domain.member.entity.MemberRole;
import com.example.studyfactory.domain.member.exception.MemberException;
import com.example.studyfactory.domain.member.repository.MemberRepository;
import com.example.studyfactory.domain.member.service.ManagerAccessPolicy;
import com.example.studyfactory.domain.staffSchedule.dto.StaffScheduleCellRequest;
import com.example.studyfactory.domain.staffSchedule.dto.StaffScheduleResponse;
import com.example.studyfactory.domain.staffSchedule.dto.StaffScheduleUpdateRequest;
import com.example.studyfactory.domain.staffSchedule.entity.StaffSchedule;
import com.example.studyfactory.domain.staffSchedule.entity.StaffScheduleShift;
import com.example.studyfactory.domain.staffSchedule.exception.StaffScheduleException;
import com.example.studyfactory.domain.staffSchedule.repository.StaffScheduleRepository;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StaffScheduleService {

    private static final List<DayOfWeek> DAYS = List.of(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY
    );
    private static final List<StaffScheduleShift> SHIFTS = List.of(StaffScheduleShift.MORNING, StaffScheduleShift.AFTERNOON);
    private static final List<String> TASK_TYPES = List.of("DISHWASHING", "SERVE");

    private final StaffScheduleRepository staffScheduleRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public List<StaffScheduleResponse> findAll(Long currentMemberId, Long branchId) {
        Member currentMember = findMember(currentMemberId);
        Long targetBranchId = ManagerAccessPolicy.resolveRequiredBranch(currentMember, branchId);
        Map<String, StaffSchedule> scheduleMap = new HashMap<>();
        staffScheduleRepository.findByBranchIdOrderByDayOfWeekAscShiftAscTaskTypeAsc(targetBranchId)
                .forEach(schedule -> scheduleMap.put(toKey(schedule.getDayOfWeek(), schedule.getShift(), schedule.getTaskType()), schedule));

        List<StaffScheduleResponse> responses = new ArrayList<>();
        for (DayOfWeek day : DAYS) {
            for (StaffScheduleShift shift : SHIFTS) {
                for (String taskType : TASK_TYPES) {
                    StaffSchedule staffSchedule = scheduleMap.get(toKey(day, shift, taskType));
                    if (staffSchedule == null) {
                        responses.add(StaffScheduleResponse.blank(targetBranchId, day, shift, taskType));
                        continue;
                    }
                    responses.add(StaffScheduleResponse.from(staffSchedule));
                }
            }
        }

        return responses;
    }

    @Transactional
    public List<StaffScheduleResponse> update(Long currentMemberId, Long branchId, StaffScheduleUpdateRequest request) {
        Member currentMember = findMember(currentMemberId);
        validateAdmin(currentMember);
        Long targetBranchId = ManagerAccessPolicy.resolveRequiredBranch(currentMember, branchId);
        validateRequest(request);
        staffScheduleRepository.deleteByBranchId(targetBranchId);
        List<StaffSchedule> schedules = request.schedules()
                .stream()
                .map(schedule -> new StaffSchedule(
                        targetBranchId,
                        schedule.dayOfWeek(),
                        schedule.shift(),
                        schedule.taskType().trim(),
                        schedule.workerName().trim()
                ))
                .toList();
        staffScheduleRepository.saveAll(schedules);

        return findAll(currentMemberId, targetBranchId);
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(MemberException::memberNotFound);
    }

    private void validateAdmin(Member member) {
        if (member.getRole() != MemberRole.ADMIN) {
            throw MemberException.forbidden();
        }
    }

    private void validateRequest(StaffScheduleUpdateRequest request) {
        if (request.schedules() == null || request.schedules().size() != DAYS.size() * SHIFTS.size() * TASK_TYPES.size()) {
            throw StaffScheduleException.invalidSchedule();
        }

        Set<String> keys = new HashSet<>();
        for (StaffScheduleCellRequest schedule : request.schedules()) {
            validateCell(schedule);
            String key = toKey(schedule.dayOfWeek(), schedule.shift(), schedule.taskType());
            if (!keys.add(key)) {
                throw StaffScheduleException.invalidSchedule();
            }
        }
    }

    private void validateCell(StaffScheduleCellRequest schedule) {
        if (schedule == null) {
            throw StaffScheduleException.invalidSchedule();
        }
        if (schedule.dayOfWeek() == null || !EnumSet.allOf(DayOfWeek.class).contains(schedule.dayOfWeek())) {
            throw StaffScheduleException.invalidSchedule();
        }
        if (!SHIFTS.contains(schedule.shift())) {
            throw StaffScheduleException.invalidSchedule();
        }
        if (schedule.taskType() == null || !TASK_TYPES.contains(schedule.taskType().trim())) {
            throw StaffScheduleException.invalidSchedule();
        }
    }

    private String toKey(DayOfWeek dayOfWeek, StaffScheduleShift shift, String taskType) {
        return dayOfWeek.name() + ":" + shift.name() + ":" + taskType.trim();
    }
}
