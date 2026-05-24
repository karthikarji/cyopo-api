package com.cyopo.admin.controller;

import com.cyopo.admin.dto.request.ChangePlanRequest;
import com.cyopo.admin.dto.request.ChangeStatusRequest;
import com.cyopo.admin.dto.response.AdminUserResponse;
import com.cyopo.admin.service.AdminUserService;
import com.cyopo.admin.dto.request.UpdateUserRequest;
import com.cyopo.common.response.ApiResponse;
import com.cyopo.common.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String plan,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(ApiResponse.success(
                adminUserService.getUsers(
                        search, plan, status, page, limit)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(adminUserService.getById(id)));
    }

    @PatchMapping("/{id}/plan")
    public ResponseEntity<ApiResponse<AdminUserResponse>> changePlan(
            @PathVariable UUID id,
            @Valid @RequestBody ChangePlanRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Plan updated successfully",
                adminUserService.changePlan(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminUserResponse>> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Status updated successfully",
                adminUserService.changeStatus(id, request)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "User updated successfully",
                adminUserService.updateUser(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable UUID id) {
        adminUserService.deleteUser(id);
        return ResponseEntity.ok(
                ApiResponse.success("User deleted successfully"));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> searchUsers(
            @RequestParam String email) {

        return ResponseEntity.ok(ApiResponse.success(
                adminUserService.searchByEmail(email)));
    }
}