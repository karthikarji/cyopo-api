package com.cyopo.billing.controller;

import com.cyopo.billing.service.GeoLocationService;
import com.cyopo.billing.service.GeoLocationService.CountryInfo;
import com.cyopo.billing.service.PlanService;
import com.cyopo.billing.service.PlanService.PlanWithPrice;
import com.cyopo.common.response.ApiResponse;
import com.cyopo.common.util.AppLogContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public endpoint — no authentication required.
 * Used by the pricing page to fetch plans, prices, and suggested payment methods.
 */
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class PlanController {

    private static final String CLASS = "PlanController";

    private final PlanService planService;
    private final GeoLocationService geoLocationService;

    /**
     * GET /api/v1/billing/plans
     * <p>
     * Detects user location from IP.
     * Returns active plans with prices in the detected currency
     * and suggested payment methods for that country.
     * No authentication required — pricing page is public.
     */
    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<PricingResponse>> getPlans(
            HttpServletRequest request) {

        String clientIp = geoLocationService.extractClientIp(request);
        CountryInfo countryInfo = geoLocationService.detectCountry(clientIp);

        AppLogContext.info(CLASS, "getPlans",
                "Pricing page request",
                "ip", clientIp,
                "country", countryInfo.countryCode(),
                "currency", countryInfo.currency());

        List<PlanWithPrice> plans = planService
                .getPlansForCurrency(countryInfo.currency());

        List<PlanResponse> planResponses = plans.stream()
                .map(p -> toPlanResponse(p, countryInfo))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(
                new PricingResponse(
                        countryInfo.countryCode(),
                        countryInfo.currency(),
                        countryInfo.gateway(),
                        countryInfo.suggestedMethods(),
                        planResponses
                )));
    }

    // ─── Private Mapper ───────────────────────────────────────────

    private PlanResponse toPlanResponse(PlanWithPrice p, CountryInfo country) {
        return new PlanResponse(
                p.plan().getId().toString(),
                p.plan().getName(),
                p.plan().getDisplayName(),
                p.plan().getDescription(),
                p.plan().getBadge(),
                p.plan().getFeatures(),
                p.plan().getSortOrder(),
                // Pricing
                p.price() != null ? p.price().getId().toString() : null,
                p.getMonthlyPrice(),
                p.getAnnualPrice(),
                p.getCurrency(),
                p.price() != null ? p.price().getGstRate() : BigDecimal.ZERO,
                p.isFree(),
                // Limits
                p.plan().getMaxPortfolios(),
                p.plan().getMaxProjectsPerPortfolio(),
                p.plan().isAllowCustomDomain(),
                p.plan().isAllowResumeUpload(),
                p.plan().isAllowAnalytics(),
                p.plan().isAllowPremiumTemplates(),
                p.plan().isRemoveBranding()
        );
    }

    // ─── Response Records ─────────────────────────────────────────

    public record PricingResponse(
            String countryCode,
            String currency,
            String gateway,
            List<String> suggestedPaymentMethods,
            List<PlanResponse> plans
    ) {
    }

    public record PlanResponse(
            String id,
            String name,
            String displayName,
            String description,
            String badge,
            List<String> features,
            int sortOrder,
            // Pricing
            String planPriceId,
            long monthlyPrice,
            long annualPrice,
            String currency,
            BigDecimal gstRate,
            boolean isFree,
            // Limits
            int maxPortfolios,
            int maxProjectsPerPortfolio,
            boolean allowCustomDomain,
            boolean allowResumeUpload,
            boolean allowAnalytics,
            boolean allowPremiumTemplates,
            boolean removeBranding
    ) {
    }
}