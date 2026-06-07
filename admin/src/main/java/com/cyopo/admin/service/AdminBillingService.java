package com.cyopo.admin.service;

import com.cyopo.admin.dto.response.*;
import com.cyopo.auth.model.Plan;
import com.cyopo.auth.repository.UserRepository;
import com.cyopo.billing.gateway.PaymentGateway;
import com.cyopo.billing.gateway.dto.GatewayRefundResponse;
import com.cyopo.billing.model.*;
import com.cyopo.billing.repository.*;
import com.cyopo.billing.service.BillingService;
import com.cyopo.billing.service.InvoicePdfService;
import com.cyopo.common.exception.BadRequestException;
import com.cyopo.common.exception.ResourceNotFoundException;
import com.cyopo.common.response.PageResponse;
import com.cyopo.common.util.AppLogContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminBillingService {

    private static final String CLASS = "AdminBillingService";

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillingOrderRepository orderRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final UserRepository userRepository;
    private final PaymentGateway paymentGateway;
    private final BillingService billingService;
    private final InvoicePdfService invoicePdfService;

    // ─── Stats ────────────────────────────────────────────────────────

    /**
     * Returns simple billing overview stats for admin dashboard.
     * Counts only — no MRR or complex aggregations.
     * All revenue amounts in INR paise.
     *
     * @throws ResourceNotFoundException if plan enum not found (misconfiguration)
     */
    @Transactional(readOnly = true)
    public AdminBillingStatsResponse getStats() {
        AppLogContext.info(CLASS, "getStats",
                "Fetching admin billing stats");

        Instant monthStart = Instant.now()
                .truncatedTo(ChronoUnit.DAYS)
                .minus(30, ChronoUnit.DAYS);

        AdminBillingStatsResponse stats = AdminBillingStatsResponse.builder()
                .totalRevenueAllTime(
                        paymentRepository.sumCapturedPayments())
                .totalRevenueThisMonth(
                        paymentRepository.sumCapturedPaymentsSince(monthStart))
                .currency("INR")
                .activeSubscriptions(
                        subscriptionRepository.countByStatus("ACTIVE"))
                .cancelledThisMonth(
                        subscriptionRepository.countCancelledSince(monthStart))
                .pastDueSubscriptions(
                        subscriptionRepository.countByStatus("PAST_DUE"))
                .freeUsers(userRepository.countByPlan(Plan.FREE))
                .premiumUsers(userRepository.countByPlan(Plan.PREMIUM))
                .proUsers(userRepository.countByPlan(Plan.PRO))
                .totalPaymentsThisMonth(
                        paymentRepository.countByStatusSince("CAPTURED", monthStart))
                .failedPaymentsThisMonth(
                        paymentRepository.countByStatusSince("FAILED", monthStart))
                .refundsThisMonth(
                        paymentRepository.countRefundedSince(monthStart))
                .unprocessedWebhooks(
                        webhookEventRepository.countByProcessed(false))
                .build();

        AppLogContext.info(CLASS, "getStats",
                "Billing stats fetched successfully",
                "activeSubscriptions", stats.getActiveSubscriptions(),
                "totalRevenueThisMonth", stats.getTotalRevenueThisMonth(),
                "unprocessedWebhooks", stats.getUnprocessedWebhooks());

        return stats;
    }

    // ─── Subscriptions ────────────────────────────────────────────────

    /**
     * Returns paginated subscriptions with optional status filter.
     * Maps entities to DTOs inside transaction to avoid LazyInitializationException.
     *
     * @param status optional filter (ACTIVE | CANCELLED | EXPIRED | PAST_DUE)
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminSubscriptionResponse> getSubscriptions(
            String status, int page, int size) {

        AppLogContext.info(CLASS, "getSubscriptions",
                "Fetching subscriptions",
                "status", status, "page", page, "size", size);

        var pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Subscription> result = status != null
                ? subscriptionRepository.findByStatus(status, pageable)
                : subscriptionRepository.findAll(pageable);

        // Map inside @Transactional — Hibernate session still open
        // accessing user.email and plan.name here is safe
        List<AdminSubscriptionResponse> mapped = result.getContent().stream()
                .map(s -> new AdminSubscriptionResponse(
                        s.getId().toString(),
                        s.getStatus(),
                        s.getBillingCycle(),
                        s.getCurrentPeriodStart().toString(),
                        s.getCurrentPeriodEnd().toString(),
                        s.isCancelAtPeriodEnd(),
                        s.getFinalAmount(),
                        s.getCurrency(),
                        s.getGateway(),
                        new AdminUserInfo(
                                s.getUser().getId().toString(),
                                s.getUser().getEmail(),
                                s.getUser().getName()),
                        new AdminPlanInfo(
                                s.getPlan().getName(),
                                s.getPlan().getDisplayName())
                ))
                .toList();

        AppLogContext.info(CLASS, "getSubscriptions",
                "Subscriptions fetched",
                "count", mapped.size(),
                "total", result.getTotalElements());

        return new PageResponse<>(mapped, result.getTotalElements(),
                page, size);
    }

    /**
     * Cancels a subscription on behalf of a user.
     * Admin reason is prefixed with [ADMIN:adminId] for audit clarity.
     *
     * @throws ResourceNotFoundException if subscription not found
     * @throws BadRequestException       if subscription is not ACTIVE
     */
    @Transactional
    public void cancelSubscription(UUID subscriptionId,
                                   String reason,
                                   String adminId) {
        AppLogContext.info(CLASS, "cancelSubscription",
                "Admin cancelling subscription",
                "subscriptionId", subscriptionId,
                "adminId", adminId);

        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> {
                    AppLogContext.warn(CLASS, "cancelSubscription",
                            "Subscription not found",
                            "subscriptionId", subscriptionId);
                    return new ResourceNotFoundException(
                            "Subscription", "id", subscriptionId);
                });

        if (!"ACTIVE".equals(sub.getStatus())) {
            AppLogContext.warn(CLASS, "cancelSubscription",
                    "Cannot cancel non-ACTIVE subscription",
                    "subscriptionId", subscriptionId,
                    "status", sub.getStatus());
            throw new BadRequestException(
                    "Only ACTIVE subscriptions can be cancelled. " +
                            "Current status: " + sub.getStatus());
        }

        sub.setCancelAtPeriodEnd(true);
        sub.setCancelledAt(Instant.now());
        sub.setCancellationReason("[ADMIN:" + adminId + "] " + reason);
        subscriptionRepository.save(sub);

        AppLogContext.info(CLASS, "cancelSubscription",
                "Subscription cancelled by admin",
                "subscriptionId", subscriptionId,
                "userId", sub.getUser().getId(),
                "periodEnd", sub.getCurrentPeriodEnd());
    }

    // ─── Payments ─────────────────────────────────────────────────────

    /**
     * Returns paginated payments with optional status filter.
     * Maps entities to DTOs inside transaction.
     *
     * @param status optional filter (CAPTURED | FAILED | REFUNDED)
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentResponse> getPayments(
            String status, int page, int size) {

        AppLogContext.info(CLASS, "getPayments",
                "Fetching payments",
                "status", status, "page", page, "size", size);

        var pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Payment> result = status != null
                ? paymentRepository.findByStatus(status, pageable)
                : paymentRepository.findAll(pageable);

        List<AdminPaymentResponse> mapped = result.getContent().stream()
                .map(p -> new AdminPaymentResponse(
                        p.getId().toString(),
                        p.getStatus(),
                        p.getTotalAmount(),
                        p.getRefundAmount(),
                        p.getCurrency(),
                        p.getPaymentMethod(),
                        p.getGatewayPaymentId(),
                        p.getCreatedAt().toString(),
                        new AdminUserInfo(
                                p.getUser().getId().toString(),
                                p.getUser().getEmail(),
                                null)
                ))
                .toList();

        AppLogContext.info(CLASS, "getPayments",
                "Payments fetched",
                "count", mapped.size(),
                "total", result.getTotalElements());

        return new PageResponse<>(mapped, result.getTotalElements(),
                page, size);
    }

    /**
     * Issues a refund for a captured payment via Razorpay.
     * <p>
     * Three protection layers against double refund:
     * 1. Status check  → REFUNDED/FAILED/EXPIRED payments rejected
     * 2. Amount check  → cannot exceed remaining refundable amount
     * 3. Gateway check → Razorpay rejects over-refunds at API level
     *
     * @throws ResourceNotFoundException if payment not found
     * @throws BadRequestException       if payment not refundable or amount invalid
     * @throws GatewayException          if Razorpay refund call fails
     */
    @Transactional
    public GatewayRefundResponse refundPayment(UUID paymentId,
                                               long amount,
                                               String reason,
                                               String adminId) {
        AppLogContext.info(CLASS, "refundPayment",
                "Admin initiating refund",
                "paymentId", paymentId,
                "amount", amount,
                "adminId", adminId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> {
                    AppLogContext.warn(CLASS, "refundPayment",
                            "Payment not found",
                            "paymentId", paymentId);
                    return new ResourceNotFoundException(
                            "Payment", "id", paymentId);
                });

        // ── Layer 1: Status check ──────────────────────────────────
        if ("REFUNDED".equals(payment.getStatus())) {
            AppLogContext.warn(CLASS, "refundPayment",
                    "Refund rejected — payment already fully refunded",
                    "paymentId", paymentId);
            throw new BadRequestException(
                    "Payment has already been fully refunded");
        }
        if ("FAILED".equals(payment.getStatus())
                || "EXPIRED".equals(payment.getStatus())) {
            AppLogContext.warn(CLASS, "refundPayment",
                    "Refund rejected — payment not in refundable state",
                    "paymentId", paymentId,
                    "status", payment.getStatus());
            throw new BadRequestException(
                    "Cannot refund a " + payment.getStatus() + " payment");
        }

        // ── Layer 2: Amount check ──────────────────────────────────
        long remaining = payment.getTotalAmount() - payment.getRefundAmount();

        if (amount <= 0) {
            throw new BadRequestException(
                    "Refund amount must be greater than zero");
        }
        if (amount > remaining) {
            AppLogContext.warn(CLASS, "refundPayment",
                    "Refund amount exceeds remaining refundable amount",
                    "paymentId", paymentId,
                    "requested", amount,
                    "remaining", remaining);
            throw new BadRequestException(
                    "Refund amount (" + amount + ") exceeds remaining " +
                            "refundable amount (" + remaining + ")");
        }

        // ── Layer 3: Gateway refund ────────────────────────────────
        GatewayRefundResponse refundResponse = paymentGateway.refund(
                payment.getGatewayPaymentId(), amount,
                "[ADMIN:" + adminId + "] " + reason);

        billingService.recordRefund(
                payment.getGatewayPaymentId(),
                amount,
                refundResponse.getRefundId());

        AppLogContext.info(CLASS, "refundPayment",
                "Refund processed successfully",
                "paymentId", paymentId,
                "refundId", refundResponse.getRefundId(),
                "amount", amount,
                "adminId", adminId);

        return refundResponse;
    }

    // ─── Invoices ─────────────────────────────────────────────────────

    /**
     * Returns paginated invoices with optional status filter.
     * Maps entities to DTOs inside transaction.
     *
     * @param status optional filter (ISSUED | PAID | VOID | DRAFT)
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminInvoiceResponse> getInvoices(
            String status, int page, int size) {

        AppLogContext.info(CLASS, "getInvoices",
                "Fetching invoices",
                "status", status, "page", page, "size", size);

        var pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Invoice> result = status != null
                ? invoiceRepository.findByStatus(status, pageable)
                : invoiceRepository.findAll(pageable);

        List<AdminInvoiceResponse> mapped = result.getContent().stream()
                .map(i -> new AdminInvoiceResponse(
                        i.getId().toString(),
                        i.getInvoiceNumber(),
                        i.getStatus(),
                        i.getTotal(),
                        i.getCurrency(),
                        i.getBillingName(),
                        i.getBillingEmail(),
                        i.getPdfUrl(),
                        i.getIssuedAt().toString(),
                        new AdminUserInfo(
                                i.getUser().getId().toString(),
                                i.getUser().getEmail(),
                                null)
                ))
                .toList();

        AppLogContext.info(CLASS, "getInvoices",
                "Invoices fetched",
                "count", mapped.size(),
                "total", result.getTotalElements());

        return new PageResponse<>(mapped, result.getTotalElements(),
                page, size);
    }

    /**
     * Voids an invoice — marks status as VOID.
     * Does NOT trigger a refund — refund separately if needed.
     *
     * @throws ResourceNotFoundException if invoice not found
     * @throws BadRequestException       if invoice is already VOID or DRAFT
     */
    @Transactional
    public void voidInvoice(UUID invoiceId, String adminId) {
        AppLogContext.info(CLASS, "voidInvoice",
                "Admin voiding invoice",
                "invoiceId", invoiceId,
                "adminId", adminId);

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> {
                    AppLogContext.warn(CLASS, "voidInvoice",
                            "Invoice not found",
                            "invoiceId", invoiceId);
                    return new ResourceNotFoundException(
                            "Invoice", "id", invoiceId);
                });

        if ("VOID".equals(invoice.getStatus())) {
            throw new BadRequestException("Invoice is already voided");
        }
        if ("DRAFT".equals(invoice.getStatus())) {
            throw new BadRequestException("Cannot void a draft invoice");
        }

        invoice.setStatus("VOID");
        invoiceRepository.save(invoice);

        AppLogContext.info(CLASS, "voidInvoice",
                "Invoice voided successfully",
                "invoiceId", invoiceId,
                "invoiceNumber", invoice.getInvoiceNumber(),
                "adminId", adminId);
    }


    /**
     * Regenerates PDF for an invoice that has no pdfUrl.
     * Used when PDF generation failed during original payment processing.
     *
     * @throws ResourceNotFoundException if invoice not found
     * @throws BadRequestException       if invoice already has a PDF
     */
    @Transactional
    public String regenerateInvoicePdf(UUID invoiceId, String adminId) {
        AppLogContext.info(CLASS, "regenerateInvoicePdf",
                "Admin regenerating invoice PDF",
                "invoiceId", invoiceId,
                "adminId", adminId);

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> {
                    AppLogContext.warn(CLASS, "regenerateInvoicePdf",
                            "Invoice not found",
                            "invoiceId", invoiceId);
                    return new ResourceNotFoundException(
                            "Invoice", "id", invoiceId);
                });

        if (invoice.getPdfUrl() != null && !invoice.getPdfUrl().isBlank()) {
            AppLogContext.warn(CLASS, "regenerateInvoicePdf",
                    "Invoice already has a PDF — returning existing URL",
                    "invoiceId", invoiceId,
                    "pdfUrl", invoice.getPdfUrl());
            // Return existing URL instead of throwing
            // Admin may click this accidentally
            return invoice.getPdfUrl();
        }

        String pdfUrl = invoicePdfService.generateAndUpload(invoice);
        if (pdfUrl == null) {
            AppLogContext.error(CLASS, "regenerateInvoicePdf",
                    "PDF generation failed",
                    "invoiceId", invoiceId);
            throw new BadRequestException(
                    "PDF generation failed. Check Cloudinary configuration.");
        }

        invoice.setPdfUrl(pdfUrl);
        invoiceRepository.save(invoice);

        AppLogContext.info(CLASS, "regenerateInvoicePdf",
                "Invoice PDF regenerated successfully",
                "invoiceId", invoiceId,
                "pdfUrl", pdfUrl);

        return pdfUrl;
    }

    // ─── Orders ───────────────────────────────────────────────────────

    /**
     * Returns paginated orders with optional status filter.
     * Maps entities to DTOs inside transaction.
     *
     * @param status optional filter (PAID | PENDING | FAILED | EXPIRED)
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminOrderResponse> getOrders(
            String status, int page, int size) {

        AppLogContext.info(CLASS, "getOrders",
                "Fetching orders",
                "status", status, "page", page, "size", size);

        var pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<BillingOrder> result = status != null
                ? orderRepository.findByStatus(status, pageable)
                : orderRepository.findAll(pageable);

        List<AdminOrderResponse> mapped = result.getContent().stream()
                .map(o -> new AdminOrderResponse(
                        o.getId().toString(),
                        o.getStatus(),
                        o.getTotalAmount(),
                        o.getCurrency(),
                        o.getBillingCycle(),
                        o.getGateway(),
                        o.getGatewayOrderId(),
                        o.getCreatedAt().toString(),
                        new AdminUserInfo(
                                o.getUser().getId().toString(),
                                o.getUser().getEmail(),
                                null),
                        new AdminPlanInfo(
                                o.getPlan().getName(),
                                o.getPlan().getDisplayName())
                ))
                .toList();

        AppLogContext.info(CLASS, "getOrders",
                "Orders fetched",
                "count", mapped.size(),
                "total", result.getTotalElements());

        return new PageResponse<>(mapped, result.getTotalElements(),
                page, size);
    }

    // ─── Webhook Events ───────────────────────────────────────────────

    /**
     * Returns paginated webhook events with optional processed filter.
     * Unprocessed events indicate missed or failed webhook processing.
     *
     * @param processed null = all, true = processed only, false = unprocessed only
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminWebhookResponse> getWebhookEvents(
            Boolean processed, int page, int size) {

        AppLogContext.info(CLASS, "getWebhookEvents",
                "Fetching webhook events",
                "processed", processed, "page", page, "size", size);

        var pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<WebhookEvent> result = processed != null
                ? webhookEventRepository.findByProcessed(processed, pageable)
                : webhookEventRepository.findAll(pageable);

        List<AdminWebhookResponse> mapped = result.getContent().stream()
                .map(w -> new AdminWebhookResponse(
                        w.getId().toString(),
                        w.getGateway(),
                        w.getEventId(),
                        w.getEventType(),
                        w.isProcessed(),
                        w.getErrorMessage(),
                        w.getRetryCount(),
                        w.getCreatedAt().toString()
                ))
                .toList();

        AppLogContext.info(CLASS, "getWebhookEvents",
                "Webhook events fetched",
                "count", mapped.size(),
                "total", result.getTotalElements());

        return new PageResponse<>(mapped, result.getTotalElements(),
                page, size);
    }
}