package com.cyopo.billing.gateway.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Response from gateway after order creation.
 * gatewayOrderId is passed to the frontend SDK.
 */
@Getter
@Builder
public class GatewayOrderResponse {
    private String gatewayOrderId;  // Razorpay order_id
    private long amount;          // echoed back
    private String currency;
    private String status;          // created | attempted | paid
}