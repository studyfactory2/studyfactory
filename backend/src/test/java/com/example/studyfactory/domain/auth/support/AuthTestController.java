package com.example.studyfactory.domain.auth.support;

import com.example.studyfactory.domain.auth.annotation.CurrentMember;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("test")
@RestController
public class AuthTestController {

    @GetMapping("/api/auth-test/me")
    public Long me(@CurrentMember Long memberId) {
        return memberId;
    }
}
