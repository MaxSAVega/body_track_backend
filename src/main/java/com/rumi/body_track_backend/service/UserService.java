package com.rumi.body_track_backend.service;

import com.rumi.body_track_backend.dto.AuthResponse;
import com.rumi.body_track_backend.dto.LoginRequest;
import com.rumi.body_track_backend.dto.RefreshRequest;
import com.rumi.body_track_backend.dto.RegisterRequest;
import com.rumi.body_track_backend.dto.UpdateProfileRequest;
import com.rumi.body_track_backend.model.User;
import com.rumi.body_track_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("El email ya está registrado");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setBirthDate(request.getBirthDate());
        user.setGender(request.getGender());
        user.setPhone(request.getPhone());
        user.setDni(request.getDni());
        user.setHeight(request.getHeight());
        user.setWeight(request.getWeight());

        userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());
        return new AuthResponse(token, refreshToken, user.getGender(), user.getName(), user.getEmail(), user.getDni(), user.getHeight(), user.getWeight());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Contraseña incorrecta");
        }

        String token = jwtService.generateToken(user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());
        return new AuthResponse(token, refreshToken, user.getGender(), user.getName(), user.getEmail(), user.getDni(), user.getHeight(), user.getWeight());
    }

    public AuthResponse refreshToken(RefreshRequest request) {
        if (!jwtService.isRefreshTokenValid(request.getRefreshToken())) {
            throw new RuntimeException("Refresh token inválido o expirado");
        }

        String email = jwtService.extractEmail(request.getRefreshToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String newToken = jwtService.generateToken(user.getEmail());
        String newRefreshToken = jwtService.generateRefreshToken(user.getEmail());
        return new AuthResponse(newToken, newRefreshToken, user.getGender(), user.getName(), user.getEmail(), user.getDni(), user.getHeight(), user.getWeight());
    }

    public AuthResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            userRepository.findByEmail(request.getEmail())
                    .filter(other -> !other.getId().equals(user.getId()))
                    .ifPresent(other -> {
                        throw new RuntimeException("El email ya está registrado");
                    });
            user.setEmail(request.getEmail());
        }
        if (request.getDni() != null) {
            user.setDni(request.getDni());
        }
        if (request.getHeight() != null) {
            user.setHeight(request.getHeight());
        }
        if (request.getWeight() != null) {
            user.setWeight(request.getWeight());
        }

        userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());
        return new AuthResponse(token, refreshToken, user.getGender(), user.getName(), user.getEmail(), user.getDni(), user.getHeight(), user.getWeight());
    }
}
