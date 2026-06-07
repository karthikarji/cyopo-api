package com.cyopo.billing.service;

import com.cyopo.billing.model.Plan;
import com.cyopo.billing.model.PlanPrice;
import com.cyopo.billing.repository.PlanPriceRepository;
import com.cyopo.billing.repository.PlanRepository;
import com.cyopo.common.exception.BadRequestException;
import com.cyopo.common.exception.ResourceNotFoundException;
import com.cyopo.common.util.AppLogContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlanService {

    private static final String CLASS = "PlanService";

    private final PlanRepository planRepository;
    private final PlanPriceRepository planPriceRepository;

    /**
     * Returns all active plans with prices for the given currency.
     * Falls back to USD if no active price exists for the currency.
     * Primary data source for the pricing page.
     *
     * @param currency ISO 4217 currency code e.g. INR, USD, GBP
     * @return list of plans paired with their price — price may be null for FREE
     */
    @Transactional(readOnly = true)
    public List<PlanWithPrice> getPlansForCurrency(String currency) {
        AppLogContext.debug(CLASS, "getPlansForCurrency",
                "Fetching plans", "currency", currency);

        List<Plan> plans = planRepository
                .findByIsActiveTrueOrderBySortOrderAsc();

        if (plans.isEmpty()) {
            AppLogContext.warn(CLASS, "getPlansForCurrency",
                    "No active plans found in DB — check billing.plans table");
        }

        List<PlanWithPrice> result = plans.stream()
                .map(plan -> {
                    // Try requested currency first, fall back to USD
                    PlanPrice price = planPriceRepository
                            .findByPlanIdAndCurrencyAndIsActiveTrue(
                                    plan.getId(), currency)
                            .orElseGet(() -> {
                                AppLogContext.debug(CLASS, "getPlansForCurrency",
                                        "No price for currency — falling back to USD",
                                        "planName", plan.getName(),
                                        "requestedCurrency", currency);
                                return planPriceRepository
                                        .findByPlanIdAndCurrencyAndIsActiveTrue(
                                                plan.getId(), "USD")
                                        .orElse(null);
                            });
                    return new PlanWithPrice(plan, price);
                })
                .toList();

        AppLogContext.info(CLASS, "getPlansForCurrency",
                "Plans fetched successfully",
                "currency", currency,
                "count", result.size());

        return result;
    }

    /**
     * Fetches a specific plan price by ID.
     * Used during order creation to lock in the price server-side.
     *
     * @param planPriceId UUID of the plan price row
     * @throws ResourceNotFoundException if plan price does not exist
     * @throws BadRequestException       if planPriceId format is invalid
     */
    @Transactional(readOnly = true)
    public PlanPrice getPlanPrice(UUID planPriceId) {
        AppLogContext.debug(CLASS, "getPlanPrice",
                "Fetching plan price", "planPriceId", planPriceId);

        return planPriceRepository.findById(planPriceId)
                .orElseThrow(() -> {
                    AppLogContext.warn(CLASS, "getPlanPrice",
                            "Plan price not found", "planPriceId", planPriceId);
                    return new ResourceNotFoundException(
                            "PlanPrice", "id", planPriceId);
                });
    }

    /**
     * Fetches a plan by name (FREE, PREMIUM, PRO).
     * Used for feature gate checks.
     *
     * @param name plan name — must match billing.plans.name exactly
     * @throws ResourceNotFoundException if plan not found
     */
    @Transactional(readOnly = true)
    public Plan getPlanByName(String name) {
        AppLogContext.debug(CLASS, "getPlanByName",
                "Fetching plan by name", "name", name);

        return planRepository.findByName(name)
                .orElseThrow(() -> {
                    AppLogContext.warn(CLASS, "getPlanByName",
                            "Plan not found — possible misconfiguration",
                            "name", name);
                    return new ResourceNotFoundException(
                            "Plan", "name", name);
                });
    }

    // ─── PlanWithPrice Record ─────────────────────────────────────

    /**
     * Pairs a plan with its price for the requested currency.
     * price is null for FREE plan or if no price is configured.
     * All getters have safe null defaults.
     */
    public record PlanWithPrice(Plan plan, PlanPrice price) {

        /**
         * Returns true if this plan is free (no payment needed).
         */
        public boolean isFree() {
            return price == null || price.getMonthlyPrice() == 0;
        }

        public long getMonthlyPrice() {
            return price != null ? price.getMonthlyPrice() : 0L;
        }

        public long getAnnualPrice() {
            return price != null ? price.getAnnualPrice() : 0L;
        }

        /**
         * Currency defaults to INR if no price configured.
         */
        public String getCurrency() {
            return price != null ? price.getCurrency() : "INR";
        }

        /**
         * Gateway defaults to RAZORPAY if no price configured.
         */
        public String getGateway() {
            return price != null ? price.getGateway() : "RAZORPAY";
        }
    }
}