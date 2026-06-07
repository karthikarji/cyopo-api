package com.cyopo.common.exception;

/**
 * Thrown when a gateway operation fails.
 * Wraps gateway-specific exceptions so services
 * don't need to import Razorpay/Stripe classes.
 */
public class GatewayException extends RuntimeException {

    public GatewayException(String message) {
        super(message);
    }

    public GatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}