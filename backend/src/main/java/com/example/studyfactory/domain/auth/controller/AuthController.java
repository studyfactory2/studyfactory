package com.example.studyfactory.domain.auth.controller;

import com.example.studyfactory.domain.auth.dto.AccessTokenReissueRequest;
import com.example.studyfactory.domain.auth.dto.AccessTokenResponse;
import com.example.studyfactory.domain.auth.dto.LoginRequest;
import com.example.studyfactory.domain.auth.dto.LoginResponse;
import com.example.studyfactory.domain.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/token/reissue")
    public AccessTokenResponse reissueAccessToken(@Valid @RequestBody AccessTokenReissueRequest request) {
        return authService.reissueAccessToken(request);
    }
}
