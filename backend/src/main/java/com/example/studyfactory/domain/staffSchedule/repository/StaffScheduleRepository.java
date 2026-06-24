package com.example.studyfactory.domain.staffSchedule.repository;

import com.example.studyfactory.domain.staffSchedule.entity.StaffSchedule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffScheduleRepository extends JpaRepository<StaffSchedule, Long> {

    List<StaffSchedule> findByBranchIdOrderByDayOfWeekAscShiftAscTaskTypeAsc(Long branchId);

    void deleteByBranchId(Long branchId);
}
