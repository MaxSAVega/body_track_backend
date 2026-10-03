package com.rumi.body_track_backend.service;

import com.rumi.body_track_backend.dto.AuthResponse;
import com.rumi.body_track_backend.dto.LoginRequest;
import com.rumi.body_track_backend.dto.RefreshRequest;
import com.rumi.body_track_backend.dto.RegisterRequest;
import com.rumi.body_track_backend.dto.UpdateProfileRequest;
import com.rumi.body_track_backend.exception.ConflictException;
import com.rumi.body_track_backend.exception.NotFoundException;
import com.rumi.body_track_backend.exception.UnauthorizedException;
import com.rumi.body_track_backend.model.RefreshToken;
import com.rumi.body_track_backend.model.User;
import com.rumi.body_track_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Registration, login, refresh, logout and profile.
 *
 * <p>Every lookup is keyed on the numeric user id taken from the verified token,
 * never on a value supplied in the request body.
 *
 * <p>All credential failures raise the same message. Distinguishing "no such user"
 * from "wrong password" is a free account-enumeration oracle, and there is no rate
 * limit that makes it safe at scale, so the distinction is not drawn here at all.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /**
     * Compared against when the email is unknown, so that a miss and a hit cost the
     * same. Without this the response time alone reveals which emails are
     * registered even though the message is identical.
     */
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static final String GENERIC_CREDENTIALS = "Credenciales inválidas";
    private static final String GENERIC_REGISTRATION =
            "No se pudo completar el registro. Revisa los datos e inténtalo de nuevo.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            // Logged precisely, reported generically: the caller must not learn
            // that the address is taken.
            log.info("register_email_taken email_hash={}", Integer.toHexString(email.hashCode()));
            throw new ConflictException(GENERIC_REGISTRATION, "registration_rejected");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setBirthDate(request.getBirthDate());
        user.setGender(request.getGender());
        user.setPhone(request.getPhone());
        user.setDni(request.getDni());
        user.setHeight(request.getHeight());
        user.setWeight(request.getWeight());

        userRepository.save(user);

        return issueSession(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            // Equalise timing against the real comparison below.
            passwordEncoder.matches(request.getPassword(), DUMMY_HASH);
            log.info("login_unknown_email email_hash={}", Integer.toHexString(email.hashCode()));
            throw new UnauthorizedException(GENERIC_CREDENTIALS);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.info("login_bad_password user_id={}", user.getId());
            throw new UnauthorizedException(GENERIC_CREDENTIALS);
        }

        return issueSession(user);
    }

    /**
     * Rotates the refresh token. The presented token is revoked and replaced, so it
     * cannot be used twice; presenting it again trips reuse detection and kills the
     * whole family.
     */
    @Transactional
    public AuthResponse refreshToken(RefreshRequest request) {
        RefreshToken consumed = refreshTokenService.consume(request.getRefreshToken());

        User user = userRepository.findById(consumed.getUserId())
                .orElseThrow(UnauthorizedException::new);

        RefreshTokenService.IssuedToken next = refreshTokenService.rotate(consumed);
        return buildAuthResponse(user, next.rawToken());
    }

    /** Real logout: revokes every refresh token belonging to the caller. */
    @Transactional
    public void logout(Long userId) {
        refreshTokenService.revokeAllForUser(userId);
    }

    @Transactional
    public AuthResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario"));

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String email = normalizeEmail(request.getEmail());
            if (!email.equals(user.getEmail())) {
                boolean takenByAnother = userRepository.findByEmail(email)
                        .map(other -> !other.getId().equals(userId))
                        .orElse(false);
                if (takenByAnother) {
                    log.info("profile_email_taken user_id={}", userId);
                    throw new ConflictException(
                            "No se pudo actualizar el perfil", "profile_rejected");
                }
                user.setEmail(email);
            }
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

        // The email may have moved, so every existing session is retired and a fresh
        // one issued. Previously tokens minted under the old address simply stopped
        // resolving, which left the caller silently logged out instead.
        refreshTokenService.revokeAllForUser(userId);
        return issueSession(user);
    }

    private AuthResponse issueSession(User user) {
        RefreshTokenService.IssuedToken issued = refreshTokenService.issueNewFamily(user.getId());
        return buildAuthResponse(user, issued.rawToken());
    }

    private AuthResponse buildAuthResponse(User user, String rawRefreshToken) {
        return new AuthResponse(
                jwtService.generateAccessToken(user.getId(), user.getEmail()),
                rawRefreshToken,
                user.getId(),
                user.getGender(),
                user.getName(),
                user.getEmail(),
                user.getDni(),
                user.getHeight(),
                user.getWeight());
    }

    /**
     * Lowercases and trims the address so that a lookup is stable regardless of how
     * the client cased it. This also removes a class of collision where two rows
     * differing only in case could share one authorization identity.
     */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}