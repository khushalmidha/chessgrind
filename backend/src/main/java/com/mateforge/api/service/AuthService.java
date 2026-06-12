package com.mateforge.api.service;

import com.mateforge.api.dto.AuthDtos.AuthResponse;
import com.mateforge.api.dto.AuthDtos.GoogleLoginRequest;
import com.mateforge.api.dto.AuthDtos.LoginRequest;
import com.mateforge.api.dto.AuthDtos.RegisterRequest;
import com.mateforge.api.model.AppUser;
import com.mateforge.api.repository.AppUserRepository;
import com.mateforge.api.security.JwtService;
import com.mateforge.api.security.UserPrincipal;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RestClient restClient;
    private final String googleClientId;

    public AuthService(
        AppUserRepository users,
        PasswordEncoder encoder,
        AuthenticationManager authenticationManager,
        JwtService jwtService,
        @Value("${app.google.client-id:}") String googleClientId
    ) {
        this.users = users;
        this.encoder = encoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.googleClientId = googleClientId;
        this.restClient = RestClient.create();
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

    @Transactional
    public AuthResponse google(GoogleLoginRequest request) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Google sign-in is not configured");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> tokenInfo = restClient.get()
            .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + request.credential()))
            .retrieve()
            .body(Map.class);
        if (tokenInfo == null || !googleClientId.equals(tokenInfo.get("aud"))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid Google token audience");
        }
        if (!"true".equals(String.valueOf(tokenInfo.get("email_verified")))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Google email is not verified");
        }

        String email = String.valueOf(tokenInfo.get("email")).toLowerCase();
        String name = String.valueOf(tokenInfo.getOrDefault("name", email.substring(0, email.indexOf('@'))));
        AppUser user = users.findByEmail(email).orElseGet(() -> {
            AppUser created = new AppUser();
            created.setEmail(email);
            created.setUsername(uniqueUsername(name));
            created.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
            return users.save(created);
        });
        return response(user);
    }

    private AuthResponse response(AppUser user) {
        UserPrincipal principal = UserPrincipal.from(user);
        return new AuthResponse(jwtService.createToken(principal), user.getId(), user.getUsername(), user.getEmail());
    }

    private String uniqueUsername(String raw) {
        String base = raw.toLowerCase().replaceAll("[^a-z0-9_]", "");
        if (base.length() < 3) {
            base = "player";
        }
        base = base.substring(0, Math.min(base.length(), 28));
        String candidate = base;
        int suffix = 1;
        while (users.existsByUsername(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }
}
