package com.cyopo.core.controller;

import com.cyopo.auth.model.User;
import com.cyopo.auth.service.AuthService;
import com.cyopo.billing.model.Plan;
import com.cyopo.billing.service.FeatureGateService;
import com.cyopo.common.response.ApiResponse;
import com.cyopo.common.util.AppLogContext;
import com.cyopo.core.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns feature gate status for the current user.
 * <p>
 * Lives in core module — avoids circular dependency:
 * billing → core would be circular (core already depends on billing)
 * core    → billing is fine ✅
 * <p>
 * Frontend uses this response to:
 * → Show/hide "Create Portfolio" button
 * → Show/hide "Custom Domain" settings
 * → Show upgrade prompts when gates are closed
 */
@RestController
@RequestMapping("/api/v1/gates")
@RequiredArgsConstructor
public class BillingGatesController {

    private static final String CLASS = "BillingGatesController";

    private final FeatureGateService featureGateService;
    private final AuthService authService;
    private final PortfolioService portfolioService;

    /**
     * GET /api/v1/gates
     * <p>
     * Returns current plan and feature gate statuses.
     * No try-catch — AuthService and PortfolioService throw
     * ResourceNotFoundException which GlobalExceptionHandler maps to 404.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<FeatureGatesResponse>> getGates(
            @AuthenticationPrincipal String userId) {

        User user = authService.findUserById(userId);
        int portfolioCount = portfolioService.countUserPortfolios(user.getId());
        Plan plan = featureGateService.getPlanForUser(user);

        return ResponseEntity.ok(ApiResponse.success(
                new FeatureGatesResponse(
                        featureGateService.canCreatePortfolio(user, portfolioCount),
                        featureGateService.canUseCustomDomain(user),
                        featureGateService.canUsePremiumTemplates(user),
                        user.getPlan().name(),
                        portfolioCount,
                        plan.getMaxPortfolios()
                )));
    }

    // ─── Response Record ──────────────────────────────────────────

    /**
     * Feature gate statuses for the current user.
     * Extend this record when new gates are added.
     * <p>
     * Not included (free for all plans):
     * canUploadResume  → always true
     * canViewAnalytics → always true
     */
    public record FeatureGatesResponse(
            boolean canCreatePortfolio,
            boolean canUseCustomDomain,
            boolean canUsePremiumTemplates,
            String currentPlan,
            int portfoliosUsed,
            int portfoliosAllowed
    ) {
    }
}