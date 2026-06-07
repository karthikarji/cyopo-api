package com.cyopo.billing.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class VerifyPaymentRequest {

    @NotBlank(message = "Gateway order ID is required")
    private String gatewayOrderId;

    @NotBlank(message = "Gateway payment ID is required")
    private String gatewayPaymentId;

    // HMAC-SHA256 signature from Razorpay — verified server-side
    @NotBlank(message = "Signature is required")
    private String signature;
}