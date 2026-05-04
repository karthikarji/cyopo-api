package com.cyopo.auth.service;

import com.cyopo.auth.model.RefreshToken;
import com.cyopo.auth.model.User;
import com.cyopo.auth.repository.RefreshTokenRepository;
import com.cyopo.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        // Delete any existing refresh tokens for this user
        // One active refresh token per user at a time
        refreshTokenRepository.deleteAllByUserId(user.getId());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusSeconds(refreshTokenExpiry))
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional(readOnly = true)
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(token)
                .orElseThrow(() -> new BadRequestException(
                        "Invalid refresh token"));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new BadRequestException(
                    "Refresh token has expired. Please login again.");
        }

        return refreshToken;
    }

    @Transactional
    public void deleteByUserId(UUID userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    // TODO: When scaling to multiple instances with high traffic,
    // migrate refresh token storage from PostgreSQL to Redis.
    // Redis TTL will handle expiry automatically — no scheduled
    // cleanup job needed. Use SETEX with 7-day TTL per token.
    // This reduces DB load and improves token lookup speed.
    @Scheduled(cron = "0 0 3 * * *") // runs every day at 3am
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Cleaning up expired refresh tokens");
        refreshTokenRepository.deleteAllExpiredTokens(Instant.now());
        log.info("Expired refresh tokens cleanup complete");
    }
}