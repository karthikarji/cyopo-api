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
            HttpServletRequest request) {

        PortfolioResponse portfolio =
                portfolioService.getPublicBySlug(slug);

        analyticsService.recordView(
                portfolio.getId(),
                portfolio.getUserId(),
                viewerUserId != null
                        ? UUID.fromString(viewerUserId) : null,
                request.getRemoteAddr()
        );

        return ResponseEntity.ok(
                ApiResponse.success("View recorded"));
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
}