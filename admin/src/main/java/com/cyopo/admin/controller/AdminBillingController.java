package com.cyopo.admin.controller;

import com.cyopo.admin.dto.response.*;
import com.cyopo.admin.service.AdminBillingService;
import com.cyopo.billing.gateway.dto.GatewayRefundResponse;
import com.cyopo.common.response.ApiResponse;
import com.cyopo.common.response.PageResponse;
import com.cyopo.common.util.AppLogContext;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Admin-only billing management endpoints.
 * All endpoints require ADMIN role — enforced via @PreAuthorize.
 * No try-catch — GlobalExceptionHandler handles all exceptions.
 */
@RestController
@RequestMapping("/api/v1/admin/billing")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminBillingController {

    private static final String CLASS = "AdminBillingController";

    private final AdminBillingService adminBillingService;

    // ─── Stats ────────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/billing/stats
     * Returns billing overview counts — revenue, subscriptions, plan breakdown.
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminBillingStatsResponse>> getStats() {
        AppLogContext.info(CLASS, "getStats",
                "Admin billing stats requested");
        return ResponseEntity.ok(
                ApiResponse.success(adminBillingService.getStats()));
    }

    // ─── Subscriptions ────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/billing/subscriptions
     * Query params: status (optional), page (default 1), size (default 20)
     */
    @GetMapping("/subscriptions")
    public ResponseEntity<ApiResponse<PageResponse<AdminSubscriptionResponse>>> getSubscriptions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") int size) {

        AppLogContext.info(CLASS, "getSubscriptions",
                "Fetching subscriptions",
                "status", status, "page", page);

        return ResponseEntity.ok(ApiResponse.success(
                adminBillingService.getSubscriptions(status, page, size)));
    }

    /**
     * POST /api/v1/admin/billing/subscriptions/:id/cancel
     * Body: { "reason": "string" }
     * Cancels at period end — user retains access until period_end.
     */
    @PostMapping("/subscriptions/{id}/cancel")
    public ResponseEntity<ApiResponse<String>> cancelSubscription(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String adminId) {

        String reason = body.getOrDefault(
                "reason", "Admin initiated cancellation");

        AppLogContext.info(CLASS, "cancelSubscription",
                "Admin cancellation request",
                "subscriptionId", id, "adminId", adminId);

        adminBillingService.cancelSubscription(id, reason, adminId);

        return ResponseEntity.ok(ApiResponse.success(
                "Subscription cancelled successfully"));
    }

    // ─── Payments ─────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/billing/payments
     * Query params: status (optional), page (default 1), size (default 20)
     */
    @GetMapping("/payments")
    public ResponseEntity<ApiResponse<PageResponse<AdminPaymentResponse>>> getPayments(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") int size) {

        AppLogContext.info(CLASS, "getPayments",
                "Fetching payments",
                "status", status, "page", page);

        return ResponseEntity.ok(ApiResponse.success(
                adminBillingService.getPayments(status, page, size)));
    }

    /**
     * POST /api/v1/admin/billing/payments/:id/refund
     * Body: { "amount": 49900, "reason": "string" }
     * Amount in smallest unit (paise for INR).
     * Protected by 3-layer double refund prevention.
     */
    @PostMapping("/payments/{id}/refund")
    public ResponseEntity<ApiResponse<GatewayRefundResponse>> refundPayment(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal String adminId) {

        long amount = ((Number) body.get("amount")).longValue();
        String reason = (String) body.getOrDefault("reason", "Admin refund");

        AppLogContext.info(CLASS, "refundPayment",
                "Admin refund request",
                "paymentId", id,
                "amount", amount,
                "adminId", adminId);

        GatewayRefundResponse response = adminBillingService
                .refundPayment(id, amount, reason, adminId);

        return ResponseEntity.ok(ApiResponse.success(
                "Refund processed successfully", response));
    }

    // ─── Invoices ─────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/billing/invoices
     * Query params: status (optional), page (default 1), size (default 20)
     */
    @GetMapping("/invoices")
    public ResponseEntity<ApiResponse<PageResponse<AdminInvoiceResponse>>> getInvoices(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") int size) {

        AppLogContext.info(CLASS, "getInvoices",
                "Fetching invoices",
                "status", status, "page", page);

        return ResponseEntity.ok(ApiResponse.success(
                adminBillingService.getInvoices(status, page, size)));
    }

    /**
     * POST /api/v1/admin/billing/invoices/:id/void
     * Marks invoice as VOID. Does NOT trigger refund.
     */
    @PostMapping("/invoices/{id}/void")
    public ResponseEntity<ApiResponse<String>> voidInvoice(
            @PathVariable UUID id,
            @AuthenticationPrincipal String adminId) {

        AppLogContext.info(CLASS, "voidInvoice",
                "Admin void invoice request",
                "invoiceId", id, "adminId", adminId);

        adminBillingService.voidInvoice(id, adminId);

        return ResponseEntity.ok(ApiResponse.success(
                "Invoice voided successfully"));
    }

    /**
     * POST /api/v1/admin/billing/invoices/:id/regenerate-pdf
     * Generates PDF for invoices that were created without one.
     * Safe to call even if PDF already exists — returns existing URL.
     */
    @PostMapping("/invoices/{id}/regenerate-pdf")
    public ResponseEntity<ApiResponse<String>> regenerateInvoicePdf(
            @PathVariable UUID id,
            @AuthenticationPrincipal String adminId) {

        AppLogContext.info(CLASS, "regenerateInvoicePdf",
                "Admin PDF regeneration request",
                "invoiceId", id, "adminId", adminId);

        String pdfUrl = adminBillingService.regenerateInvoicePdf(id, adminId);

        return ResponseEntity.ok(ApiResponse.success(
                "Invoice PDF generated successfully", pdfUrl));
    }

    // ─── Orders ───────────────────────────────────────────────────────

    /**
     * GET /api/v1/admin/billing/orders
     * Query params: status (optional), page (default 1), size (default 20)
     * Read-only — orders cannot be modified via admin API.
     */
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<PageResponse<AdminOrderResponse>>> getOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") int size) {

        AppLogContext.info(CLASS, "getOrders",
                "Fetching orders",
                "status", status, "page", page);

        return ResponseEntity.ok(ApiResponse.success(
                adminBillingService.getOrders(status, page, size)));
    }

    // ─── Webhook Events ───────────────────────────────────────────────

    /**
     * GET /api/v1/admin/billing/webhook-events
     * Query params: processed (optional boolean), page (default 1), size (default 20)
     * Unprocessed events indicate missed webhooks — check for issues.
     */
    @GetMapping("/webhook-events")
    public ResponseEntity<ApiResponse<PageResponse<AdminWebhookResponse>>> getWebhookEvents(
            @RequestParam(required = false) Boolean processed,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") int size) {

        AppLogContext.info(CLASS, "getWebhookEvents",
                "Fetching webhook events",
                "processed", processed, "page", page);

        return ResponseEntity.ok(ApiResponse.success(
                adminBillingService.getWebhookEvents(processed, page, size)));
    }
}