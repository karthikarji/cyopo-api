package com.cyopo.admin.service;

import com.cyopo.admin.dto.request.ChangePlanRequest;
import com.cyopo.admin.dto.request.ChangeStatusRequest;
import com.cyopo.admin.dto.request.UpdateUserRequest;
import com.cyopo.admin.dto.response.AdminUserResponse;
import com.cyopo.auth.model.User;
import com.cyopo.auth.model.UserStatus;
import com.cyopo.auth.repository.UserRepository;
import com.cyopo.auth.service.RefreshTokenService;
import com.cyopo.common.exception.ResourceNotFoundException;
import com.cyopo.common.response.PageResponse;
import com.cyopo.core.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getUsers(
            String search,
            String plan,
            String status,
            int page,
            int limit) {

        PageRequest pageable = PageRequest.of(
                page - 1, limit,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> result = (search != null && !search.isBlank())
                ? userRepository
                .findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        search, search, pageable)
                : userRepository.findAll(pageable);

        return new PageResponse<>(
                result.getContent().stream()
                        .map(u -> AdminUserResponse.from(
                                u,
                                (int) portfolioRepository
                                        .countByUserId(u.getId())))
                        .toList(),
                result.getTotalElements(),
                page,
                limit
        );
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getById(UUID id) {
        User user = findById(id);
        return AdminUserResponse.from(
                user,
                (int) portfolioRepository.countByUserId(id));
    }

    @Transactional
    public AdminUserResponse changePlan(UUID id,
                                        ChangePlanRequest request) {
        User user = findById(id);
        user.setPlan(request.getPlan());
        User updated = userRepository.save(user);
        log.info("Admin changed plan for {} to {}",
                user.getEmail(), request.getPlan());
        return AdminUserResponse.from(
                updated,
                (int) portfolioRepository.countByUserId(id));
    }

    @Transactional
    public AdminUserResponse changeStatus(UUID id,
                                          ChangeStatusRequest request) {
        User user = findById(id);
        user.setStatus(request.getStatus());
        User updated = userRepository.save(user);

        // Invalidate all sessions when suspending
        if (request.getStatus() == UserStatus.SUSPENDED) {
            refreshTokenService.deleteByUserId(id);
            log.info("Sessions invalidated for suspended user: {}",
                    user.getEmail());
        }

        log.info("Admin changed status for {} to {}",
                user.getEmail(), request.getStatus());
        return AdminUserResponse.from(
                updated,
                (int) portfolioRepository.countByUserId(id));
    }

    @Transactional
    public AdminUserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = findById(id);

        if (request.getName() != null && !request.getName().isBlank())
            user.setName(request.getName().trim());

        if (request.getPlan() != null)
            user.setPlan(request.getPlan());

        if (request.getStatus() != null) {
            // Invalidate sessions if suspending
            if (request.getStatus() == UserStatus.SUSPENDED
                    && user.getStatus() != UserStatus.SUSPENDED) {
                refreshTokenService.deleteByUserId(id);
            }
            user.setStatus(request.getStatus());
        }

        User updated = userRepository.save(user);
        log.info("Admin updated user: {}", user.getEmail());
        return AdminUserResponse.from(
                updated,
                (int) portfolioRepository.countByUserId(id));
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = findById(id);
        refreshTokenService.deleteByUserId(id);
        userRepository.delete(user);
        log.info("Admin deleted user: {}", user.getEmail());
    }

    private User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User", "id", id));
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> searchByEmail(String email) {
        if (email == null || email.trim().length() < 2) return List.of();
        return userRepository
                .findTop5ByEmailContainingIgnoreCase(email.trim())
                .stream()
                .map(u -> AdminUserResponse.from(u, 0))
                .toList();
    }
}