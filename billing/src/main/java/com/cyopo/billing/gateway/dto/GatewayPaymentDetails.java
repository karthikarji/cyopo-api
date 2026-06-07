package com.cyopo.billing.gateway.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Full payment details fetched from gateway.
 * Used for verification and reconciliation.
 */
@Getter
@Builder
public class GatewayPaymentDetails {
    private String gatewayPaymentId;
    private String gatewayOrderId;
    private long amount; // captured amount
    private String currency;
    private String status; // captured | failed
    private String method; // upi | card | netbanking
    private String email;
    private String contact;
    private String errorCode;
    private String errorDescription;
    private Map<String, Object> raw; // full gateway response
}