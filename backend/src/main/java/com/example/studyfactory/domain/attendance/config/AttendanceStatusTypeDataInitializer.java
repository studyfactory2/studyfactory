package com.example.studyfactory.domain.attendance.config;

import com.example.studyfactory.domain.attendance.entity.AttendanceStatusType;
import com.example.studyfactory.domain.attendance.repository.AttendanceStatusTypeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AttendanceStatusTypeDataInitializer implements ApplicationRunner {

    private static final List<String> BUILT_IN_STATUS_NAMES = List.of("출석");

    private final AttendanceStatusTypeRepository attendanceStatusTypeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String name : BUILT_IN_STATUS_NAMES) {
            if (attendanceStatusTypeRepository.findByName(name).isEmpty()) {
                attendanceStatusTypeRepository.save(new AttendanceStatusType(name, false));
            }
        }
    }
}
