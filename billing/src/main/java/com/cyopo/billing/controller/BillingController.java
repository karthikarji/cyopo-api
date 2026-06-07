package com.cyopo.billing.controller;

import com.cyopo.auth.model.User;
import com.cyopo.auth.service.AuthService;
import com.cyopo.billing.dto.request.CreateOrderRequest;
import com.cyopo.billing.dto.request.VerifyPaymentRequest;
import com.cyopo.billing.dto.response.CreateOrderResponse;
import com.cyopo.billing.dto.response.InvoiceResponse;
import com.cyopo.billing.dto.response.SubscriptionResponse;
import com.cyopo.billing.dto.response.VerifyPaymentResponse;
import com.cyopo.billing.model.Coupon;
import com.cyopo.billing.model.Invoice;
import com.cyopo.billing.model.Subscription;
import com.cyopo.billing.service.BillingService;
import com.cyopo.billing.service.GeoLocationService;
import com.cyopo.common.exception.BadRequestException;
import com.cyopo.common.response.ApiResponse;
import com.cyopo.common.util.AppLogContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles all billing-related operations:
 * order creation, payment verification, coupon validation,
 * invoice history, subscription status and cancellation.
 * <p>
 * No try-catch here — GlobalExceptionHandler handles all exceptions.
 */
@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private static final String CLASS = "BillingController";

    private final BillingService billingService;
    private final GeoLocationService geoLocationService;
    private final AuthService authService;

    // ─── Create Order ─────────────────────────────────────────────

    /**
     * POST /api/v1/billing/create-order
     * <p>
     * Creates a Razorpay order for the selected plan.
     * All amounts calculated server-side.
     * Returns gatewayOrderId — passed to Razorpay SDK in frontend.
     * If coupon gives 100% off, gatewayOrderId is null and amount is 0.
     */
    @PostMapping("/create-order")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CreateOrderResponse>> createOrder(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreateOrderRequest request,
            HttpServletRequest httpRequest) {

        AppLogContext.info(CLASS, "createOrder",
                "Create order request received", "userId", userId);

        User user = authService.findUserById(userId);
        String clientIp = geoLocationService.extractClientIp(httpRequest);
        String countryCode = geoLocationService.detectCountry(clientIp)
                .countryCode();

        CreateOrderResponse response = billingService.createOrder(
                user, request, clientIp, countryCode);

        AppLogContext.info(CLASS, "createOrder",
                "Order created successfully",
                "userId", userId,
                "orderId", response.getOrderId(),
                "amount", response.getAmount());

        return ResponseEntity.ok(
                ApiResponse.success("Order created", response));
    }

    // ─── Verify Payment ───────────────────────────────────────────

    /**
     * POST /api/v1/billing/verify
     * <p>
     * Called after user completes payment in Razorpay SDK.
     * Verifies HMAC-SHA256 signature and activates subscription.
     */
    @PostMapping("/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<VerifyPaymentResponse>> verifyPayment(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody VerifyPaymentRequest request) {

        AppLogContext.info(CLASS, "verifyPayment",
                "Payment verification request received",
                "userId", userId,
                "gatewayOrderId", request.getGatewayOrderId());

        User user = authService.findUserById(userId);
        VerifyPaymentResponse response =
                billingService.verifyAndActivate(user, request);

        AppLogContext.info(CLASS, "verifyPayment",
                "Payment verified and subscription activated",
                "userId", userId,
                "plan", response.getPlanName());

        return ResponseEntity.ok(
                ApiResponse.success("Payment verified successfully", response));
    }

    // ─── Validate Coupon ──────────────────────────────────────────

    /**
     * POST /api/v1/billing/validate-coupon
     * <p>
     * Validates coupon code and returns discount details.
     * Does NOT redeem — redemption only happens after payment.
     */
    @PostMapping("/validate-coupon")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CouponValidationResponse>> validateCoupon(
            @AuthenticationPrincipal String userId,
            @RequestBody Map<String, String> body) {

        String code = body.get("code");

        // Validate request — throw BadRequestException
        // GlobalExceptionHandler maps this to 400
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Coupon code is required");
        }

        AppLogContext.info(CLASS, "validateCoupon",
                "Coupon validation request",
                "userId", userId, "code", code);

        Coupon coupon = billingService.validateCoupon(
                code, UUID.fromString(userId));

        return ResponseEntity.ok(ApiResponse.success(
                new CouponValidationResponse(
                        coupon.getCode(),
                        coupon.getDiscountType().name(),
                        coupon.getDiscountValue(),
                        "Coupon applied successfully")));
    }

    // ─── Invoice History ──────────────────────────────────────────

    /**
     * GET /api/v1/billing/invoices
     * Returns the authenticated user's invoice history,
     * ordered by most recent first.
     * Returns DTOs — no lazy loading issues.
     */
    @GetMapping("/invoices")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> getInvoices(
            @AuthenticationPrincipal String userId) {

        AppLogContext.info(CLASS, "getInvoices",
                "Fetching invoices", "userId", userId);

        List<InvoiceResponse> invoices = billingService
                .getInvoices(UUID.fromString(userId));

        AppLogContext.info(CLASS, "getInvoices",
                "Invoices fetched", "userId", userId,
                "count", invoices.size());

        return ResponseEntity.ok(ApiResponse.success(invoices));
    }

    // ─── Subscription Status ──────────────────────────────────────

    /**
     * GET /api/v1/billing/subscription
     * Returns the user's current active subscription.
     * Returns null data if no active subscription exists.
     * Returns DTO — no lazy loading issues.
     */
    @GetMapping("/subscription")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getSubscription(
            @AuthenticationPrincipal String userId) {

        AppLogContext.debug(CLASS, "getSubscription",
                "Fetching subscription", "userId", userId);

        return billingService
                .getActiveSubscription(UUID.fromString(userId))
                .map(sub -> {
                    AppLogContext.info(CLASS, "getSubscription",
                            "Active subscription found",
                            "userId", userId,
                            "status", sub.status(),
                            "plan", sub.planName());
                    return ResponseEntity.ok(
                            ApiResponse.success(sub));
                })
                .orElseGet(() -> {
                    AppLogContext.info(CLASS, "getSubscription",
                            "No active subscription found",
                            "userId", userId);
                    return ResponseEntity.ok(
                            ApiResponse.<SubscriptionResponse>success(null));
                });
    }

    // ─── Cancel Subscription ──────────────────────────────────────

    /**
     * POST /api/v1/billing/cancel
     * <p>
     * Cancels subscription at period end (Option B).
     * User retains full access until current_period_end.
     * Body: { "reason": "optional cancellation reason" }
     */
    @PostMapping("/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> cancelSubscription(
            @AuthenticationPrincipal String userId,
            @RequestBody(required = false) Map<String, String> body) {

        String reason = body != null ? body.get("reason") : null;

        AppLogContext.info(CLASS, "cancelSubscription",
                "Cancellation request received",
                "userId", userId, "reason", reason);

        User user = authService.findUserById(userId);
        billingService.cancelSubscription(user, reason);

        return ResponseEntity.ok(ApiResponse.success(
                "Subscription cancelled. " +
                        "You will retain access until the end of your billing period."));
    }

    // ─── Response Record ──────────────────────────────────────────

    public record CouponValidationResponse(
            String code,
            String discountType,
            BigDecimal discountValue,
            String message
    ) {
    }
}