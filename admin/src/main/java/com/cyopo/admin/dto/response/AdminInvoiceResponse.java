package com.cyopo.admin.dto.response;

/**
 * Invoice data returned by admin billing API.
 * Mapped from Invoice entity inside @Transactional.
 */
public record AdminInvoiceResponse(
        String id,
        String invoiceNumber,
        String status,
        long total,
        String currency,
        String billingName,
        String billingEmail,
        String pdfUrl,
        String issuedAt,
        AdminUserInfo user
) {
}