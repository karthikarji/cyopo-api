package com.cyopo.auth.controller;

import com.cyopo.auth.dto.request.UpdateUserRequest;
import com.cyopo.auth.dto.response.UserResponse;
import com.cyopo.auth.service.AuthService;
import com.cyopo.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal String userId) {
        UserResponse user = authService.getCurrentUser(userId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse user = authService.updateUser(userId, request);
        return ResponseEntity.ok(
                ApiResponse.success("User updated successfully", user));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @AuthenticationPrincipal String userId) {
        authService.deleteUser(userId);
        return ResponseEntity.ok(
                ApiResponse.success("Account deleted successfully"));
    }
}