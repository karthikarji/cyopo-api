package com.cyopo.core.controller;

import com.cyopo.common.response.ApiResponse;
import com.cyopo.common.response.PageResponse;
import com.cyopo.core.dto.request.CreatePortfolioRequest;
import com.cyopo.core.dto.request.PortfolioStatusRequest;
import com.cyopo.core.dto.request.UpdatePortfolioRequest;
import com.cyopo.core.dto.response.PortfolioResponse;
import com.cyopo.core.model.PortfolioStatus;
import com.cyopo.core.service.PortfolioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/user/portfolios")
@RequiredArgsConstructor
public class UserPortfolioController {

    private final PortfolioService portfolioService;  // ← only service, nothing else

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PortfolioResponse>>>
    getUserPortfolios(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) PortfolioStatus status,
            @RequestParam(required = false) UUID templateId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(ApiResponse.success(
                portfolioService.getUserPortfolios(
                        userId, status, templateId, page, limit)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PortfolioResponse>> create(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreatePortfolioRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Portfolio created successfully",
                        portfolioService.create(userId, request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PortfolioResponse>> getById(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id) {

        return ResponseEntity.ok(ApiResponse.success(
                portfolioService.getById(userId, id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PortfolioResponse>> update(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id,
            @RequestBody UpdatePortfolioRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                "Portfolio updated successfully",
                portfolioService.update(userId, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id) {

        portfolioService.delete(userId, id);
        return ResponseEntity.ok(
                ApiResponse.success("Portfolio deleted successfully"));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<PortfolioResponse>> updateStatus(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id,
            @Valid @RequestBody PortfolioStatusRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                "Portfolio status updated",
                portfolioService.updateStatus(userId, id, request)));
    }

    @PatchMapping("/{id}/duplicate")
    public ResponseEntity<ApiResponse<PortfolioResponse>> duplicate(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Portfolio duplicated successfully",
                        portfolioService.duplicate(userId, id)));
    }

    @GetMapping("/preview/{slug}")
    public ResponseEntity<ApiResponse<PortfolioResponse>> preview(
            @AuthenticationPrincipal String userId,
            @PathVariable String slug) {

        return ResponseEntity.ok(ApiResponse.success(
                portfolioService.previewBySlug(userId, slug)));
    }

    // ─── Profile Photo ────────────────────────────────────────────────

    @PostMapping("/{portfolioId}/profile-photo")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadProfilePhoto(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID portfolioId,
            @RequestParam("file") MultipartFile file) {
        try {
            String url = portfolioService.uploadProfilePhoto(
                    userId, portfolioId, file);
            return ResponseEntity.ok(ApiResponse.success(
                    "Profile photo uploaded",
                    Map.of("url", url)));
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @DeleteMapping("/{portfolioId}/profile-photo")
    public ResponseEntity<ApiResponse<Void>> deleteProfilePhoto(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID portfolioId) {
        try {
            portfolioService.deleteProfilePhoto(userId, portfolioId);
            return ResponseEntity.ok(
                    ApiResponse.success("Profile photo removed"));
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }
}