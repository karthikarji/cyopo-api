package com.cyopo.admin.controller;

import com.cyopo.admin.dto.request.CreateCouponRequest;
import com.cyopo.admin.dto.request.UpdateCouponRequest;
import com.cyopo.admin.dto.response.AdminCouponResponse;
import com.cyopo.admin.dto.response.AdminCouponRedemptionResponse;
import com.cyopo.admin.service.AdminCouponService;
import com.cyopo.common.response.ApiResponse;
import com.cyopo.common.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/coupons")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCouponController {

    private final AdminCouponService adminCouponService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminCouponResponse>>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(ApiResponse.success(
                adminCouponService.getAll(search, page, limit)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminCouponResponse>> getById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(adminCouponService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminCouponResponse>> create(
            @AuthenticationPrincipal String adminId,
            @Valid @RequestBody CreateCouponRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Coupon created successfully",
                        adminCouponService.create(
                                UUID.fromString(adminId), request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminCouponResponse>> update(
            @PathVariable UUID id,
            @RequestBody UpdateCouponRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                "Coupon updated successfully",
                adminCouponService.update(id, request)));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<AdminCouponResponse>> toggleActive(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                adminCouponService.toggleActive(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id) {
        adminCouponService.delete(id);
        return ResponseEntity.ok(
                ApiResponse.success("Coupon deleted successfully"));
    }

    @GetMapping("/{id}/redemptions")
    public ResponseEntity<ApiResponse<List<AdminCouponRedemptionResponse>>>
    getRedemptions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                adminCouponService.getRedemptions(id)));
    }
}