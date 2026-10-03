package com.rumi.body_track_backend.controller;

import com.rumi.body_track_backend.dto.AuthResponse;
import com.rumi.body_track_backend.dto.LoginRequest;
import com.rumi.body_track_backend.dto.RefreshRequest;
import com.rumi.body_track_backend.dto.RegisterRequest;
import com.rumi.body_track_backend.dto.UpdateProfileRequest;
import com.rumi.body_track_backend.security.AuthPrincipal;
import com.rumi.body_track_backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(userService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(userService.refreshToken(request));
    }

    /**
     * Revokes every refresh token of the caller, which is what makes a stolen
     * credential useless from this point on. Access tokens keep working until they
     * expire on their own; with no denylist that is the best available guarantee,
     * which is why the access token lifetime stays short.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal) {
        userService.logout(principal.userId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/profile")
    public ResponseEntity<AuthResponse> updateProfile(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(principal.userId(), request));
    }
}