package com.cyopo.admin.dto.response;

/**
 * Subscription data returned by admin billing API.
 * Mapped from Subscription entity inside @Transactional
 * to avoid LazyInitializationException on user/plan fields.
 */
public record AdminSubscriptionResponse(
        String id,
        String status,
        String billingCycle,
        String currentPeriodStart,
        String currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        long finalAmount,
        String currency,
        String gateway,
        AdminUserInfo user,
        AdminPlanInfo plan
) {
}