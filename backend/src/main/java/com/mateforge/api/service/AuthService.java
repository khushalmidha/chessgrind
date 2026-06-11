package com.mateforge.api.service;

import com.mateforge.api.dto.AuthDtos.AuthResponse;
import com.mateforge.api.dto.AuthDtos.LoginRequest;
import com.mateforge.api.dto.AuthDtos.RegisterRequest;
import com.mateforge.api.model.AppUser;
import com.mateforge.api.repository.AppUserRepository;
import com.mateforge.api.security.JwtService;
import com.mateforge.api.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AppUserRepository users, PasswordEncoder encoder, AuthenticationManager authenticationManager, JwtService jwtService) {
        this.users = users;
        this.encoder = encoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        if (users.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "Username is already taken");
        }
        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(encoder.encode(request.password()));
        users.save(user);
        return response(user);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email().toLowerCase(), request.password()));
        AppUser user = users.findByEmail(request.email().toLowerCase())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        return response(user);
    }

    private AuthResponse response(AppUser user) {
        UserPrincipal principal = UserPrincipal.from(user);
        return new AuthResponse(jwtService.createToken(principal), user.getId(), user.getUsername(), user.getEmail());
    }
}
