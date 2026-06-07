package com.cyopo.admin.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Simple billing stats for admin overview.
 * Intentionally kept minimal — Option A (counts only, no MRR).
 */
@Getter
@Builder
public class AdminBillingStatsResponse {

    // ─── Revenue ──────────────────────────────────────────────────
    long totalRevenueAllTime;     // sum of all CAPTURED payments (paise)
    long totalRevenueThisMonth;   // sum of CAPTURED payments this month
    String currency;              // always INR for now

    // ─── Subscriptions ────────────────────────────────────────────
    long activeSubscriptions;     // status = ACTIVE
    long cancelledThisMonth;      // cancelledAt this month
    long pastDueSubscriptions;    // status = PAST_DUE

    // ─── Plans breakdown ──────────────────────────────────────────
    long freeUsers;               // plan = FREE
    long premiumUsers;            // plan = PREMIUM
    long proUsers;                // plan = PRO

    // ─── Payments ─────────────────────────────────────────────────
    long totalPaymentsThisMonth;  // count of CAPTURED payments this month
    long failedPaymentsThisMonth; // count of FAILED payments this month
    long refundsThisMonth;        // count of REFUNDED payments this month

    // ─── Webhooks ─────────────────────────────────────────────────
    long unprocessedWebhooks;     // processed = false
}