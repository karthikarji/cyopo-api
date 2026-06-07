// GatewayRefundResponse.java
package com.cyopo.billing.gateway.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Response from gateway after initiating a refund.
 */
@Getter
@Builder
public class GatewayRefundResponse {
    private String refundId;    // Razorpay refund_id
    private long amount;      // refunded amount
    private String currency;
    private String status;      // initiated | processed | failed
    private Instant createdAt;
}