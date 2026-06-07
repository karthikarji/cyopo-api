package com.cyopo.billing.dto.response;

/**
 * Compact subscription data for user-facing billing section.
 * Avoids lazy loading — all fields extracted inside @Transactional.
 */
public record SubscriptionResponse(
        String id,
        String status,
        String billingCycle,
        String currentPeriodStart,
        String currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        String cancelledAt,
        String currency,
        long finalAmount,
        String gateway,
        String planName,       // extra — used by BillingCurrentPlan
        String planDisplayName // extra — used by BillingCurrentPlan
) {
}