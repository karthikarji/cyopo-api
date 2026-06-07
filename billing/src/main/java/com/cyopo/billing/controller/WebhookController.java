package com.cyopo.billing.controller;

import com.cyopo.billing.gateway.PaymentGateway;
import com.cyopo.billing.gateway.dto.GatewayPaymentDetails;
import com.cyopo.billing.model.BillingOrder;
import com.cyopo.billing.model.WebhookEvent;
import com.cyopo.billing.repository.BillingOrderRepository;
import com.cyopo.billing.repository.WebhookEventRepository;
import com.cyopo.billing.service.BillingService;
import com.cyopo.common.util.AppLogContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Receives and processes webhook events from Razorpay.
 * <p>
 * Design rules:
 * → Always returns HTTP 200 — Razorpay retries on any other status
 * → Never throws — all errors are caught, stored, and returned as "ok"
 * → Idempotency via UNIQUE(gateway, event_id) in webhook_events table
 * → Try-catch is correct here — one event failure must not block others
 * and we must always return 200 to prevent infinite Razorpay retries
 */
@RestController
@RequestMapping("/api/v1/billing/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private static final String CLASS = "WebhookController";
    private static final String GATEWAY = "RAZORPAY";

    private final PaymentGateway paymentGateway;
    private final WebhookEventRepository webhookEventRepository;
    private final BillingOrderRepository orderRepository;
    private final BillingService billingService;
    private final ObjectMapper objectMapper;

    /**
     * POST /api/v1/billing/webhook
     * <p>
     * Processes incoming Razorpay webhook events.
     * Actively handles: payment.captured, payment.failed, refund.created
     * All other events are stored for audit but not processed.
     */
    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature",
                    required = false) String signature) {

        // ── Step 1: Verify signature ───────────────────────────────
        // Reject spoofed webhooks — invalid signature = not from Razorpay
        // Return 200 so Razorpay does not retry a rejected request
        if (signature == null
                || !paymentGateway.verifyWebhookSignature(
                rawPayload, signature)) {
            AppLogContext.warn(CLASS, "handleWebhook",
                    "Invalid webhook signature — possible spoofing attempt");
            return ResponseEntity.ok("rejected");
        }

        // ── Step 2: Parse payload ──────────────────────────────────
        // Try-catch required — malformed JSON must not crash the endpoint
        Map<String, Object> payload;
        String eventId;
        String eventType;

        try {
            payload = objectMapper.readValue(rawPayload, Map.class);
            eventId = (String) payload.get("id");
            eventType = (String) payload.get("event");

            if (eventId == null || eventType == null) {
                AppLogContext.warn(CLASS, "handleWebhook",
                        "Webhook payload missing id or event field");
                return ResponseEntity.ok("invalid_payload");
            }
        } catch (Exception e) {
            AppLogContext.error(CLASS, "handleWebhook",
                    "Failed to parse webhook payload", e);
            return ResponseEntity.ok("parse_error");
        }

        AppLogContext.info(CLASS, "handleWebhook",
                "Webhook received",
                "eventId", eventId,
                "eventType", eventType);

        // ── Step 3: Idempotency check ──────────────────────────────
        // UNIQUE(gateway, event_id) prevents double processing
        // Razorpay retries webhooks — this is the safety net
        if (webhookEventRepository.existsByGatewayAndEventId(
                GATEWAY, eventId)) {
            AppLogContext.debug(CLASS, "handleWebhook",
                    "Duplicate webhook — already processed, skipping",
                    "eventId", eventId);
            return ResponseEntity.ok("duplicate");
        }

        // ── Step 4: Persist event record ───────────────────────────
        // Try-catch for race condition — two instances saving same event
        WebhookEvent event = WebhookEvent.builder()
                .gateway(GATEWAY)
                .eventId(eventId)
                .eventType(eventType)
                .payload(payload)
                .processed(false)
                .build();

        try {
            event = webhookEventRepository.save(event);
        } catch (DataIntegrityViolationException e) {
            // Race condition — another instance saved it first
            AppLogContext.debug(CLASS, "handleWebhook",
                    "Race condition — webhook already saved by another instance",
                    "eventId", eventId);
            return ResponseEntity.ok("duplicate");
        }

        // ── Step 5: Process event ──────────────────────────────────
        // Try-catch required — one processing failure must not:
        //   a) crash the endpoint
        //   b) prevent marking the error for retry tracking
        //   c) return non-200 (would cause infinite Razorpay retries)
        try {
            switch (eventType) {
                case "payment.captured" -> processPaymentCaptured(payload);
                case "payment.failed" -> processPaymentFailed(payload);
                case "refund.created" -> processRefundCreated(payload);
                default -> AppLogContext.debug(CLASS, "handleWebhook",
                        "Unhandled event type — stored for audit only",
                        "eventType", eventType);
            }

            // Mark event as successfully processed
            event.setProcessed(true);
            event.setProcessedAt(Instant.now());
            webhookEventRepository.save(event);

            AppLogContext.info(CLASS, "handleWebhook",
                    "Webhook processed successfully",
                    "eventId", eventId, "eventType", eventType);

        } catch (Exception e) {
            // Record failure for manual investigation / retry tracking
            // Do NOT rethrow — must return 200 to stop Razorpay infinite retries
            // After 3 retries Razorpay stops — reconciliation job handles the rest
            AppLogContext.error(CLASS, "handleWebhook",
                    "Webhook processing failed — recorded for retry",
                    e,
                    "eventId", eventId,
                    "eventType", eventType,
                    "retryCount", event.getRetryCount() + 1);

            event.setErrorMessage(e.getMessage());
            event.setRetryCount(event.getRetryCount() + 1);
            event.setLastRetryAt(Instant.now());
            webhookEventRepository.save(event);
        }

        return ResponseEntity.ok("ok");
    }

    // ─── Event Processors ─────────────────────────────────────────

    /**
     * payment.captured — most critical event.
     * Activates subscription if /verify endpoint did not already process it.
     * This handles the browser-close-mid-payment scenario.
     */
    private void processPaymentCaptured(Map<String, Object> payload) {
        Map<String, Object> paymentData = extractPaymentData(payload);
        String gatewayPaymentId = (String) paymentData.get("id");
        String gatewayOrderId = (String) paymentData.get("order_id");

        AppLogContext.info(CLASS, "processPaymentCaptured",
                "Processing payment.captured",
                "gatewayPaymentId", gatewayPaymentId,
                "gatewayOrderId", gatewayOrderId);

        BillingOrder order = orderRepository
                .findByGatewayOrderId(gatewayOrderId)
                .orElse(null);

        if (order == null) {
            // Could be a Payment Link or other Razorpay product — not our order
            AppLogContext.warn(CLASS, "processPaymentCaptured",
                    "Order not found — may belong to a different system",
                    "gatewayOrderId", gatewayOrderId);
            return;
        }

        // Already PAID — /verify endpoint processed it before webhook arrived
        if ("PAID".equals(order.getStatus())) {
            AppLogContext.debug(CLASS, "processPaymentCaptured",
                    "Order already PAID — skipping webhook activation",
                    "orderId", order.getId());
            return;
        }

        GatewayPaymentDetails details = GatewayPaymentDetails.builder()
                .gatewayPaymentId(gatewayPaymentId)
                .gatewayOrderId(gatewayOrderId)
                .amount(((Number) paymentData.get("amount")).longValue())
                .currency((String) paymentData.get("currency"))
                .status("captured")
                .method((String) paymentData.get("method"))
                .email((String) paymentData.get("email"))
                .contact((String) paymentData.get("contact"))
                .raw(paymentData)
                .build();

        // activateSubscription() is idempotent — safe to call even if
        // /verify already activated (it checks inside before proceeding)
        billingService.activateSubscription(
                order, order.getUser(), details, gatewayPaymentId);

        AppLogContext.info(CLASS, "processPaymentCaptured",
                "Subscription activated via webhook",
                "orderId", order.getId(),
                "userId", order.getUser().getId());
    }

    /**
     * payment.failed — records failure so user can retry with a different method.
     * Only updates PENDING orders — never overrides PAID.
     */
    private void processPaymentFailed(Map<String, Object> payload) {
        Map<String, Object> paymentData = extractPaymentData(payload);
        String gatewayOrderId = (String) paymentData.get("order_id");

        AppLogContext.info(CLASS, "processPaymentFailed",
                "Processing payment.failed",
                "gatewayOrderId", gatewayOrderId);

        orderRepository.findByGatewayOrderId(gatewayOrderId)
                .ifPresentOrElse(order -> {
                    if ("PENDING".equals(order.getStatus())) {
                        order.setStatus("FAILED");
                        order.setFailureCode(
                                (String) paymentData.get("error_code"));
                        order.setFailureDescription(
                                (String) paymentData.get("error_description"));
                        orderRepository.save(order);
                        AppLogContext.info(CLASS, "processPaymentFailed",
                                "Order marked FAILED",
                                "orderId", order.getId(),
                                "failureCode", order.getFailureCode());
                    } else {
                        // Order already PAID — payment.failed arrived late, ignore
                        AppLogContext.debug(CLASS, "processPaymentFailed",
                                "Order not PENDING — ignoring late failure event",
                                "orderId", order.getId(),
                                "status", order.getStatus());
                    }
                }, () -> AppLogContext.warn(CLASS, "processPaymentFailed",
                        "Order not found for failed payment",
                        "gatewayOrderId", gatewayOrderId));
    }

    /**
     * refund.created — updates payment record with refund details.
     */
    private void processRefundCreated(Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> refundWrapper =
                (Map<String, Object>) ((Map<?, ?>) payload.get("payload"))
                        .get("refund");

        @SuppressWarnings("unchecked")
        Map<String, Object> refund =
                (Map<String, Object>) refundWrapper.get("entity");

        String gatewayPaymentId = (String) refund.get("payment_id");
        long refundAmount = ((Number) refund.get("amount")).longValue();
        String refundId = (String) refund.get("id");

        AppLogContext.info(CLASS, "processRefundCreated",
                "Processing refund.created",
                "gatewayPaymentId", gatewayPaymentId,
                "refundAmount", refundAmount,
                "refundId", refundId);

        billingService.recordRefund(gatewayPaymentId, refundAmount, refundId);
    }

    // ─── Helper ───────────────────────────────────────────────────

    /**
     * Extracts the payment entity from Razorpay webhook payload structure.
     * Razorpay payload: { payload: { payment: { entity: { ... } } } }
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPaymentData(Map<String, Object> payload) {
        Map<String, Object> payloadInner =
                (Map<String, Object>) payload.get("payload");
        Map<String, Object> paymentWrapper =
                (Map<String, Object>) payloadInner.get("payment");
        return (Map<String, Object>) paymentWrapper.get("entity");
    }
}