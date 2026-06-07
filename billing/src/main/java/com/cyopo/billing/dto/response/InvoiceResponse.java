package com.cyopo.billing.dto.response;

import java.math.BigDecimal;

/**
 * Compact invoice data for user-facing billing section.
 * Avoids lazy loading — all fields extracted inside @Transactional.
 */
public record InvoiceResponse(
        String id,
        String invoiceNumber,
        String status,
        String currency,
        long subtotal,
        long discount,
        BigDecimal gstRate,
        long gstAmount,
        long total,
        String billingName,
        String billingEmail,
        String pdfUrl,
        String periodStart,
        String periodEnd,
        String issuedAt,
        String paidAt
) {
}
