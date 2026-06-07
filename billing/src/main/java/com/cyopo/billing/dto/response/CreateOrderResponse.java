package com.cyopo.billing.dto.response;

import com.cyopo.billing.service.BillingService.AmountBreakdown;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreateOrderResponse {
    private String orderId;
    private String gatewayOrderId;  // passed to Razorpay SDK
    private long amount;
    private String currency;
    private String planName;
    private String billingCycle;
    private AmountBreakdown breakdown;
}