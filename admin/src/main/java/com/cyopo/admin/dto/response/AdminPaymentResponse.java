package com.cyopo.admin.dto.response;

/**
 * Payment data returned by admin billing API.
 * Mapped from Payment entity inside @Transactional.
 */
public record AdminPaymentResponse(
        String id,
        String status,
        long totalAmount,
        long refundAmount,
        String currency,
        String paymentMethod,
        String gatewayPaymentId,
        String createdAt,
        AdminUserInfo user
) {
}