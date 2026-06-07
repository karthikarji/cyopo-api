package com.cyopo.billing.gateway;

import com.cyopo.billing.gateway.dto.GatewayOrderRequest;
import com.cyopo.billing.gateway.dto.GatewayOrderResponse;
import com.cyopo.billing.gateway.dto.GatewayPaymentDetails;
import com.cyopo.billing.gateway.dto.GatewayRefundResponse;

/**
 * Abstraction layer over payment gateways.
 * Swap Razorpay for Stripe by providing a new implementation —
 * zero changes needed in controllers or services.
 */
public interface PaymentGateway {

    /**
     * Creates a payment order on the gateway.
     * Called before showing the checkout UI to the user.
     */
    GatewayOrderResponse createOrder(GatewayOrderRequest request);

    /**
     * Verifies the payment signature after user completes payment.
     * Prevents tampered payments from activating subscriptions.
     */
    boolean verifyPaymentSignature(String gatewayOrderId,
                                   String gatewayPaymentId,
                                   String signature);

    /**
     * Fetches full payment details from the gateway.
     * Used during reconciliation and verification.
     */
    GatewayPaymentDetails fetchPayment(String gatewayPaymentId);

    /**
     * Initiates a refund for a captured payment.
     * Admin-only — never called from user-facing flows.
     */
    GatewayRefundResponse refund(String gatewayPaymentId,
                                 long amount,
                                 String reason);

    /**
     * Verifies a webhook signature.
     * Called on every incoming webhook before processing.
     */
    boolean verifyWebhookSignature(String rawPayload, String signature);

    /**
     * Returns the gateway name — used for logging and routing.
     */
    String getGatewayName();
}