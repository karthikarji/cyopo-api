package com.cyopo.billing.gateway.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Request to create a payment order on the gateway.
 * Amount is always in smallest unit (paise for INR, cents for USD).
 */
@Getter
@Builder
public class GatewayOrderRequest {
    private long amount;           // in smallest unit
    private String currency;         // INR | USD | GBP
    private String receiptId;        // our order ID — for tracking
    private String idempotencyKey;   // prevents duplicate orders
}