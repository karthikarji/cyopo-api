package com.cyopo.core.controller;

import com.cyopo.common.response.ApiResponse;
import com.cyopo.common.response.PageResponse;
import com.cyopo.core.dto.request.CreatePortfolioRequest;
import com.cyopo.core.dto.request.PortfolioStatusRequest;
import com.cyopo.core.dto.request.UpdatePortfolioRequest;
import com.cyopo.core.dto.response.PortfolioResponse;
import com.cyopo.core.dto.response.SlugValidationResponse;
import com.cyopo.core.model.PortfolioStatus;
import com.cyopo.core.service.PortfolioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user/portfolios")
@RequiredArgsConstructor
public class UserPortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PortfolioResponse>>>
    getUserPortfolios(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) PortfolioStatus status,
            @RequestParam(required = false) UUID templateId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {

        PageResponse<PortfolioResponse> response =
                portfolioService.getUserPortfolios(
                        userId, status, templateId, page, limit);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PortfolioResponse>> create(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreatePortfolioRequest request) {

        PortfolioResponse response =
                portfolioService.create(userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Portfolio created successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PortfolioResponse>> getById(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        portfolioService.getById(userId, id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PortfolioResponse>> update(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id,
            @RequestBody UpdatePortfolioRequest request) {

        PortfolioResponse response =
                portfolioService.update(userId, id, request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Portfolio updated successfully", response));
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

        PortfolioResponse response =
                portfolioService.updateStatus(userId, id, request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Portfolio status updated", response));
    }

    @PatchMapping("/{id}/duplicate")
    public ResponseEntity<ApiResponse<PortfolioResponse>> duplicate(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID id) {

        PortfolioResponse response =
                portfolioService.duplicate(userId, id);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Portfolio duplicated successfully", response));
    }

    @GetMapping("/preview/{slug}")
    public ResponseEntity<ApiResponse<PortfolioResponse>> preview(
            @AuthenticationPrincipal String userId,
            @PathVariable String slug) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        portfolioService.previewBySlug(userId, slug)));
    }
}