package com.cyopo.core.controller;

import com.cyopo.common.response.ApiResponse;
import com.cyopo.common.response.PageResponse;
import com.cyopo.core.dto.request.ContactFormRequest;
import com.cyopo.core.dto.response.PortfolioResponse;
import com.cyopo.core.dto.response.SlugValidationResponse;
import com.cyopo.core.service.AnalyticsService;
import com.cyopo.core.service.ContactService;
import com.cyopo.core.service.PortfolioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/portfolios")
@RequiredArgsConstructor
public class PublicPortfolioController {

    private final PortfolioService portfolioService;
    private final AnalyticsService analyticsService;
    private final ContactService contactService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PortfolioResponse>>>
    getPublicPortfolios(
            @RequestParam(required = false) String plan,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        portfolioService.getPublicPortfolios(
                                plan, page, limit)));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<PortfolioResponse>> getBySlug(
            @PathVariable String slug) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        portfolioService.getPublicBySlug(slug)));
    }

    @PostMapping("/{slug}/view")
    public ResponseEntity<ApiResponse<Void>> recordView(
            @PathVariable String slug,
            @AuthenticationPrincipal String viewerUserId,
            @RequestHeader(value = "X-Session-Token", required = false)
            String sessionToken,
            HttpServletRequest request) {

        PortfolioResponse portfolio =
                portfolioService.getPublicBySlug(slug);

        // Resolve viewer UUID — null for anonymous or "anonymousUser"
        UUID viewerUUID = null;
        if (viewerUserId != null
                && !viewerUserId.equals("anonymousUser")) {
            try {
                viewerUUID = UUID.fromString(viewerUserId);
            } catch (IllegalArgumentException e) {
                // Not a valid UUID — treat as anonymous
                viewerUUID = null;
            }
        }

        // Skip if owner is viewing their own portfolio
        if (viewerUUID != null &&
                viewerUUID.toString().equals(
                        portfolio.getUserId().toString())) {
            return ResponseEntity.ok(
                    ApiResponse.success("View recorded"));
        }

        analyticsService.recordView(
                portfolio.getId(),
                portfolio.getUserId(),
                viewerUUID,
                sessionToken,
                getClientIp(request)
        );

        return ResponseEntity.ok(ApiResponse.success("View recorded"));
    }

    @PostMapping("/{slug}/contact")
    public ResponseEntity<ApiResponse<Void>> contact(
            @PathVariable String slug,
            @Valid @RequestBody ContactFormRequest request) {

        contactService.sendContact(slug, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Thank you for reaching out! " +
                                "I will get back to you soon."));
    }

    @GetMapping("/validate-slug")
    public ResponseEntity<ApiResponse<SlugValidationResponse>>
    validateSlug(
            @RequestParam String slug,
            @RequestParam(required = false) UUID excludeId) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        portfolioService.validateSlug(slug, excludeId)));
    }

    // Get real IP — checks X-Forwarded-For header first
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim(); // first IP in chain
        }
        return request.getRemoteAddr();
    }
}