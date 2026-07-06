package com.mateforge.api.service;

import com.mateforge.api.dto.AuthDtos.AuthResponse;
import com.mateforge.api.dto.AuthDtos.GoogleLoginRequest;
import com.mateforge.api.dto.AuthDtos.LoginRequest;
import com.mateforge.api.dto.AuthDtos.RegisterRequest;
import com.mateforge.api.model.AppUser;
import com.mateforge.api.repository.AppUserRepository;
import com.mateforge.api.security.JwtService;
import com.mateforge.api.security.UserPrincipal;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

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
        String email = request.email().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        if (users.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "Username is already taken");
        }
        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(request.password()));
        users.save(user);
        return response(user);
        // FIXED: duplicate-email checks used raw case while saved emails are lowercased, causing avoidable 500s.
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email().toLowerCase(), request.password()));
        } catch (AuthenticationException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            // FIXED: failed password authentication bubbled up as a generic 500 instead of a 401.
        }
        AppUser user = users.findByEmail(request.email().toLowerCase())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        return response(user);
    }

    @Transactional
    public AuthResponse google(GoogleLoginRequest request) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Google sign-in is not configured");
        }
        Map<String, Object> tokenInfo = googleTokenInfo(request.credential());
        if (tokenInfo == null || !googleClientId.equals(tokenInfo.get("aud"))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid Google token audience");
        }
        if (!"true".equals(String.valueOf(tokenInfo.get("email_verified")))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Google email is not verified");
        }

        Object rawEmail = tokenInfo.get("email");
        if (!(rawEmail instanceof String googleEmail) || !googleEmail.contains("@")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Google token did not include a valid email");
        }
        String email = googleEmail.toLowerCase();
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> googleTokenInfo(String credential) {
        try {
            return restClient.get()
                .uri(UriComponentsBuilder.fromHttpUrl("https://oauth2.googleapis.com/tokeninfo")
                    .queryParam("id_token", credential)
                    .build()
                    .encode()
                    .toUri())
                .retrieve()
                .body(Map.class);
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Google sign-in token could not be verified");
            // FIXED: Google token verification failures bubbled up as 500 Unexpected server error.
        }
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
