package com.example.studyfactory.domain.beverage.controller;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import com.example.studyfactory.domain.beverage.dto.BeveragePreferenceResponse;
import com.example.studyfactory.domain.beverage.dto.BeverageRequest;
import com.example.studyfactory.domain.beverage.dto.MemberBeverageResponse;
import com.example.studyfactory.domain.beverage.service.BeverageService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/beverages")
public class BeverageController {

    private final BeverageService beverageService;

    @GetMapping("/members")
    public List<MemberBeverageResponse> findMemberBeverages(
            @CurrentMember Long currentMemberId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long branchId
    ) {
        return beverageService.findMemberBeverages(currentMemberId, name, branchId);
    }

    @GetMapping("/me")
    public BeveragePreferenceResponse findMyDrink(@CurrentMember Long memberId) {
        return beverageService.findMyDrink(memberId);
    }

    @PatchMapping("/me")
    public BeveragePreferenceResponse updateDrink(@CurrentMember Long memberId, @Valid @RequestBody BeverageRequest request) {
        return beverageService.updateDrink(memberId, request);
    }

    @PostMapping("/me")
    public BeveragePreferenceResponse addDrink(@CurrentMember Long memberId, @Valid @RequestBody BeverageRequest request) {
        return beverageService.addDrink(memberId, request);
    }

    @PostMapping("/members/{memberId}")
    public BeveragePreferenceResponse addDrinkForMember(
            @CurrentMember Long currentMemberId,
            @PathVariable Long memberId,
            @Valid @RequestBody BeverageRequest request
    ) {
        return beverageService.addDrinkForMember(currentMemberId, memberId, request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDrink(@CurrentMember Long memberId) {
        beverageService.deleteDrink(memberId);
    }

    @DeleteMapping("/me/items")
    public BeveragePreferenceResponse deleteDrinkItem(
            @CurrentMember Long memberId,
            @RequestParam String drinkSetting
    ) {
        return beverageService.deleteDrinkItem(memberId, drinkSetting);
    }

    @DeleteMapping("/members/{memberId}/items")
    public BeveragePreferenceResponse deleteDrinkItemForMember(
            @CurrentMember Long currentMemberId,
            @PathVariable Long memberId,
            @RequestParam String drinkSetting
    ) {
        return beverageService.deleteDrinkItemForMember(currentMemberId, memberId, drinkSetting);
    }
}
