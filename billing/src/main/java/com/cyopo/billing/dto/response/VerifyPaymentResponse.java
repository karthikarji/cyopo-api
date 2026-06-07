package com.cyopo.billing.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class VerifyPaymentResponse {
    private boolean success;
    private String planName;
    private String subscriptionId;
    private String invoiceId;
    private Instant periodEnd;
}