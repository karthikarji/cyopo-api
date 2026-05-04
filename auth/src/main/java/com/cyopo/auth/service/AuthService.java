package com.cyopo.auth.service;

import com.cyopo.auth.dto.request.LoginRequest;
import com.cyopo.auth.dto.request.RegisterRequest;
import com.cyopo.auth.dto.request.UpdateUserRequest;
import com.cyopo.auth.dto.response.AuthResponse;
import com.cyopo.auth.dto.response.UserResponse;
import com.cyopo.auth.event.UserRegisteredEvent;
import com.cyopo.auth.model.RefreshToken;
import com.cyopo.auth.model.User;
import com.cyopo.auth.repository.UserRepository;
import com.cyopo.auth.security.JwtService;
import com.cyopo.common.exception.BadRequestException;
import com.cyopo.common.exception.ConflictException;
import com.cyopo.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final ApplicationEventPublisher eventPublisher;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException(
                    "An account with this email already exists");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        User saved = userRepository.save(user);
        log.info("New user registered: {}", saved.getEmail());

        // TODO: RabbitMQ - replace with publishing to
        // "user.registered" exchange for multi-instance support
        eventPublisher.publishEvent(new UserRegisteredEvent(
                this,
                saved.getEmail(),
                saved.getName(),
                saved.getId().toString()
        ));

        String accessToken = jwtService.generateAccessToken(saved);
        RefreshToken refreshToken = refreshTokenService
                .createRefreshToken(saved);

        return AuthResponse.of(
                accessToken,
                refreshToken.getToken(),
                UserResponse.from(saved)
        );
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "email", request.getEmail()));

        if (!user.isActive()) {
            throw new BadRequestException(
                    "Your account has been suspended. Please contact support.");
        }

        log.info("User logged in: {}", user.getEmail());
        String accessToken = jwtService.generateAccessToken(user);
        RefreshToken refreshToken = refreshTokenService
                .createRefreshToken(user);

        return AuthResponse.of(
                accessToken,
                refreshToken.getToken(),
                UserResponse.from(user)
        );
    }

    @Transactional
    public AuthResponse refresh(String token) {
        RefreshToken refreshToken = refreshTokenService
                .validateRefreshToken(token);

        User user = refreshToken.getUser();

        // Rotate refresh token — old one deleted, new one issued
        String newAccessToken = jwtService.generateAccessToken(user);
        RefreshToken newRefreshToken = refreshTokenService
                .createRefreshToken(user);

        log.info("Tokens refreshed for user: {}", user.getEmail());

        return AuthResponse.of(
                newAccessToken,
                newRefreshToken.getToken(),
                UserResponse.from(user)
        );
    }

    @Transactional
    public void logout(String userId) {
        refreshTokenService.deleteByUserId(UUID.fromString(userId));
        log.info("User logged out: {}", userId);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String userId) {
        User user = findUserById(userId);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateUser(String userId,
                                   UpdateUserRequest request) {
        User user = findUserById(userId);

        if (request.getName() != null) {
            user.setName(request.getName());
        }

        if (request.getEmail() != null
                && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new ConflictException(
                        "An account with this email already exists");
            }
            user.setEmail(request.getEmail().toLowerCase());
        }

        User updated = userRepository.save(user);
        log.info("User updated: {}", updated.getEmail());
        return UserResponse.from(updated);
    }

    @Transactional
    public void deleteUser(String userId) {
        User user = findUserById(userId);
        refreshTokenService.deleteByUserId(user.getId());
        userRepository.delete(user);
        log.info("User deleted: {}", user.getEmail());
    }

    private User findUserById(String userId) {
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "id", userId));
    }
}