package com.cyopo.billing.gateway;

import com.cyopo.billing.gateway.dto.GatewayOrderRequest;
import com.cyopo.billing.gateway.dto.GatewayOrderResponse;
import com.cyopo.billing.gateway.dto.GatewayPaymentDetails;
import com.cyopo.billing.gateway.dto.GatewayRefundResponse;
import com.cyopo.common.exception.GatewayException;
import com.cyopo.common.util.AppLogContext;
import com.razorpay.*;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Razorpay implementation of PaymentGateway.
 * All Razorpay-specific logic is contained here —
 * nothing Razorpay-specific leaks into services.
 * <p>
 * Try-catch pattern:
 * RazorpayException → caught here, wrapped in GatewayException
 * GatewayException  → bubbles to BillingService → GlobalExceptionHandler
 * Never swallowed   → caller always knows when gateway fails
 */
@Component
public class RazorpayGateway implements PaymentGateway {

    private static final String CLASS = "RazorpayGateway";

    private final RazorpayClient client;
    private final String keySecret;
    private final String webhookSecret;

    public RazorpayGateway(
            @Value("${app.billing.razorpay.key-id}") String keyId,
            @Value("${app.billing.razorpay.key-secret}") String keySecret,
            @Value("${app.billing.razorpay.webhook-secret}") String webhookSecret
    ) throws RazorpayException {
        this.client = new RazorpayClient(keyId, keySecret);
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
        AppLogContext.info(CLASS, "init",
                "RazorpayGateway initialized successfully");
    }

    @Override
    public String getGatewayName() {
        return "RAZORPAY";
    }

    // ─── Create Order ──────────────────────────────────────────────

    /**
     * Creates a payment order on Razorpay.
     * Razorpay deduplicates by receipt ID — same receipt = same order returned.
     *
     * @throws GatewayException if Razorpay API call fails
     */
    @Override
    public GatewayOrderResponse createOrder(GatewayOrderRequest request) {
        AppLogContext.info(CLASS, "createOrder",
                "Creating Razorpay order",
                "receiptId", request.getReceiptId(),
                "amount", request.getAmount(),
                "currency", request.getCurrency());
        try {
            JSONObject options = new JSONObject();
            options.put("amount", request.getAmount());
            options.put("currency", request.getCurrency());
            options.put("receipt", request.getReceiptId());
            options.put("payment_capture", 1); // auto-capture on payment

            Order order = client.orders.create(options);
            JSONObject orderJson = new JSONObject(order.toString());

            String gatewayOrderId = getStringSafe(orderJson, "id");

            AppLogContext.info(CLASS, "createOrder",
                    "Razorpay order created",
                    "gatewayOrderId", gatewayOrderId,
                    "receiptId", request.getReceiptId());

            return GatewayOrderResponse.builder()
                    .gatewayOrderId(gatewayOrderId)
                    .amount(((Number) order.get("amount")).longValue())
                    .currency(getStringSafe(orderJson, "currency"))
                    .status(getStringSafe(orderJson, "status"))
                    .build();

        } catch (RazorpayException e) {
            AppLogContext.error(CLASS, "createOrder",
                    "Failed to create Razorpay order", e,
                    "receiptId", request.getReceiptId(),
                    "razorpayError", e.getMessage());
            throw new GatewayException("Failed to create payment order", e);
        }
    }

    // ─── Verify Payment Signature ─────────────────────────────────

    /**
     * Verifies Razorpay payment signature using HMAC-SHA256.
     * Formula: HMAC-SHA256(keySecret, orderId + "|" + paymentId)
     * Prevents tampered payment IDs from activating subscriptions.
     * <p>
     * Returns false instead of throwing — caller decides how to handle.
     */
    @Override
    public boolean verifyPaymentSignature(String gatewayOrderId,
                                          String gatewayPaymentId,
                                          String signature) {
        try {
            String payload = gatewayOrderId + "|" + gatewayPaymentId;
            String expected = hmacSha256(payload, keySecret);
            boolean valid = expected.equals(signature);

            if (valid) {
                AppLogContext.info(CLASS, "verifyPaymentSignature",
                        "Signature verified successfully",
                        "gatewayOrderId", gatewayOrderId);
            } else {
                AppLogContext.warn(CLASS, "verifyPaymentSignature",
                        "Signature mismatch — possible payment tampering",
                        "gatewayOrderId", gatewayOrderId,
                        "gatewayPaymentId", gatewayPaymentId);
            }
            return valid;

        } catch (Exception e) {
            AppLogContext.error(CLASS, "verifyPaymentSignature",
                    "Signature verification threw exception", e,
                    "gatewayOrderId", gatewayOrderId);
            return false;
        }
    }

    // ─── Fetch Payment Details ────────────────────────────────────

    /**
     * Fetches full payment details from Razorpay.
     * Used during payment verification and reconciliation.
     * <p>
     * Uses getStringSafe() — some fields (error_code, email, contact)
     * can be JSON null and cannot be cast to String directly.
     *
     * @throws GatewayException if Razorpay API call fails
     */
    @Override
    public GatewayPaymentDetails fetchPayment(String gatewayPaymentId) {
        AppLogContext.info(CLASS, "fetchPayment",
                "Fetching payment from Razorpay",
                "gatewayPaymentId", gatewayPaymentId);
        try {
            Payment payment = client.payments.fetch(gatewayPaymentId);
            JSONObject json = new JSONObject(payment.toString());

            String status = getStringSafe(json, "status");

            AppLogContext.info(CLASS, "fetchPayment",
                    "Payment fetched",
                    "gatewayPaymentId", gatewayPaymentId,
                    "status", status,
                    "method", getStringSafe(json, "method"));

            return GatewayPaymentDetails.builder()
                    .gatewayPaymentId(getStringSafe(json, "id"))
                    .gatewayOrderId(getStringSafe(json, "order_id"))
                    .amount(((Number) payment.get("amount")).longValue())
                    .currency(getStringSafe(json, "currency"))
                    .status(status)
                    .method(getStringSafe(json, "method"))
                    .email(getStringSafe(json, "email"))
                    .contact(getStringSafe(json, "contact"))
                    .errorCode(getStringSafe(json, "error_code"))
                    .errorDescription(getStringSafe(json, "error_description"))
                    .raw(new JSONObject(payment.toString()).toMap())
                    .build();

        } catch (RazorpayException e) {
            AppLogContext.error(CLASS, "fetchPayment",
                    "Failed to fetch payment from Razorpay", e,
                    "gatewayPaymentId", gatewayPaymentId,
                    "razorpayError", e.getMessage());
            throw new GatewayException("Failed to fetch payment details", e);
        }
    }

    // ─── Refund ───────────────────────────────────────────────────

    /**
     * Initiates a refund for a captured payment.
     * Admin-only — never called from user-facing flows.
     * Supports partial refunds (pass amount < original).
     *
     * @throws GatewayException if Razorpay API call fails
     */
    @Override
    public GatewayRefundResponse refund(String gatewayPaymentId,
                                        long amount,
                                        String reason) {
        AppLogContext.info(CLASS, "refund",
                "Initiating Razorpay refund",
                "gatewayPaymentId", gatewayPaymentId,
                "amount", amount,
                "reason", reason);
        try {
            JSONObject options = new JSONObject();
            options.put("amount", amount);
            options.put("notes", new JSONObject().put("reason", reason));

            Refund refund = client.payments.refund(gatewayPaymentId, options);
            JSONObject refundJson = new JSONObject(refund.toString());

            String refundId = getStringSafe(refundJson, "id");

            AppLogContext.info(CLASS, "refund",
                    "Refund initiated successfully",
                    "refundId", refundId,
                    "gatewayPaymentId", gatewayPaymentId,
                    "amount", amount);

            return GatewayRefundResponse.builder()
                    .refundId(refundId)
                    .amount(((Number) refund.get("amount")).longValue())
                    .currency(getStringSafe(refundJson, "currency"))
                    .status(getStringSafe(refundJson, "status"))
                    .createdAt(Instant.ofEpochSecond(
                            ((Number) refund.get("created_at")).longValue()))
                    .build();

        } catch (RazorpayException e) {
            AppLogContext.error(CLASS, "refund",
                    "Failed to process refund", e,
                    "gatewayPaymentId", gatewayPaymentId,
                    "amount", amount,
                    "razorpayError", e.getMessage());
            throw new GatewayException("Failed to process refund", e);
        }
    }

    // ─── Verify Webhook Signature ─────────────────────────────────

    /**
     * Verifies Razorpay webhook signature using HMAC-SHA256.
     * Formula: HMAC-SHA256(webhookSecret, rawPayload)
     * Must be verified before processing ANY webhook event.
     * <p>
     * Returns false instead of throwing — WebhookController handles rejection.
     */
    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signature) {
        try {
            String expected = hmacSha256(rawPayload, webhookSecret);
            boolean valid = expected.equals(signature);

            if (!valid) {
                AppLogContext.warn(CLASS, "verifyWebhookSignature",
                        "Webhook signature mismatch — possible spoofed request");
            }
            return valid;

        } catch (Exception e) {
            AppLogContext.error(CLASS, "verifyWebhookSignature",
                    "Webhook signature verification threw exception", e);
            return false;
        }
    }

    // ─── Private Helpers ──────────────────────────────────────────

    /**
     * Safely extracts a String field from a Razorpay JSONObject.
     * Razorpay returns JSONObject$Null for optional fields (email, contact,
     * error_code etc.) — these cannot be cast to String directly.
     * This method handles both Java null and JSON null safely.
     */
    private String getStringSafe(org.json.JSONObject json, String key) {
        try {
            if (!json.has(key) || json.isNull(key)) return null;
            return json.getString(key);
        } catch (Exception e) {
            AppLogContext.debug(CLASS, "getStringSafe",
                    "Field not present or null in Razorpay response",
                    "key", key);
            return null;
        }
    }

    /**
     * Computes HMAC-SHA256 of data using secret.
     * Used for both payment signature and webhook signature verification.
     */
    private String hmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}