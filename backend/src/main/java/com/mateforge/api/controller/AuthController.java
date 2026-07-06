package com.mateforge.api.controller;

import com.mateforge.api.dto.AuthDtos.AuthResponse;
import com.mateforge.api.dto.AuthDtos.GoogleLoginRequest;
import com.mateforge.api.dto.AuthDtos.LoginRequest;
import com.mateforge.api.dto.AuthDtos.RegisterRequest;
import com.mateforge.api.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/google")
    AuthResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        return auth.google(request);
    }
}
