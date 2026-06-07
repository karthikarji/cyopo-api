package com.cyopo.billing.config;

import com.cyopo.billing.gateway.PaymentGateway;
import com.cyopo.billing.gateway.RazorpayGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Gateway routing config.
 * When Stripe is added — inject both and route by currency/country.
 * For now Razorpay is the only active gateway.
 */
@Configuration
public class PaymentGatewayConfig {

    /**
     * Primary gateway — used when no specific routing is needed.
     * Switch to StripeGateway here when going international.
     */
    @Bean
    @Primary
    public PaymentGateway primaryPaymentGateway(RazorpayGateway razorpayGateway) {
        return razorpayGateway;
    }
}