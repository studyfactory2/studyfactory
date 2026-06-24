package com.example.studyfactory.domain.sideDish.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.sideDish.dto.DailySideDishResponse;
import com.example.studyfactory.domain.sideDish.dto.SideDishCreateRequest;
import com.example.studyfactory.domain.sideDish.dto.SideDishResponse;
import com.example.studyfactory.domain.sideDish.service.SideDishService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/side-dishes")
public class SideDishController {

    private final SideDishService sideDishService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SideDishResponse create(@CurrentMember Long memberId, @Valid @RequestBody SideDishCreateRequest request) {
        return sideDishService.create(memberId, request);
    }

    @GetMapping("/me")
    public List<SideDishResponse> findMineByDate(@CurrentMember Long memberId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return sideDishService.findMineByDate(memberId, date);
    }

    @GetMapping("/daily")
    public List<DailySideDishResponse> findDaily(
            @CurrentMember Long memberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long branchId
    ) {
        return sideDishService.findDaily(memberId, date, branchId);
    }

    @DeleteMapping("/{sideDishId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentMember Long memberId, @PathVariable Long sideDishId) {
        sideDishService.delete(memberId, sideDishId);
    }
}
