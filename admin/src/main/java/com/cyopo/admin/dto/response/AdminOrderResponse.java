package com.cyopo.admin.dto.response;

/**
 * Order data returned by admin billing API.
 * Mapped from BillingOrder entity inside @Transactional.
 */
public record AdminOrderResponse(
        String id,
        String status,
        long totalAmount,
        String currency,
        String billingCycle,
        String gateway,
        String gatewayOrderId,
        String createdAt,
        AdminUserInfo user,
        AdminPlanInfo plan
) {
}