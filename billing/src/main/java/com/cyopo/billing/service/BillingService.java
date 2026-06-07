package com.cyopo.billing.service;

import com.cyopo.auth.model.User;
import com.cyopo.auth.repository.UserRepository;
import com.cyopo.billing.dto.request.CreateOrderRequest;
import com.cyopo.billing.dto.request.VerifyPaymentRequest;
import com.cyopo.billing.dto.response.CreateOrderResponse;
import com.cyopo.billing.dto.response.InvoiceResponse;
import com.cyopo.billing.dto.response.SubscriptionResponse;
import com.cyopo.billing.dto.response.VerifyPaymentResponse;
import com.cyopo.billing.gateway.PaymentGateway;
import com.cyopo.billing.gateway.dto.GatewayOrderRequest;
import com.cyopo.billing.gateway.dto.GatewayOrderResponse;
import com.cyopo.billing.gateway.dto.GatewayPaymentDetails;
import com.cyopo.billing.model.*;
import com.cyopo.billing.repository.*;
import com.cyopo.common.util.AppLogContext;
import com.cyopo.common.exception.BadRequestException;
import com.cyopo.common.exception.GatewayException;
import com.cyopo.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingService {

    private static final String CLASS = "BillingService";

    private final PlanRepository planRepository;
    private final PlanPriceRepository planPriceRepository;
    private final BillingOrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final PlanChangeLogRepository planChangeLogRepository;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final PaymentGateway paymentGateway;
    private final InvoicePdfService invoicePdfService;

    @Value("${app.billing.seller-gstin:}")
    private String sellerGstin;

    // ─── Create Order ──────────────────────────────────────────────

    /**
     * Creates a billing order and corresponding Razorpay gateway order.
     * All amounts are calculated server-side — client amounts are never trusted.
     *
     * @throws BadRequestException   if plan is FREE or price is invalid
     * @throws IllegalStateException if idempotency key already exists
     * @throws GatewayException      if Razorpay order creation fails
     */
    @Transactional
    public CreateOrderResponse createOrder(User user,
                                           CreateOrderRequest request,
                                           String clientIp,
                                           String countryCode) {
        String method = "createOrder";
        AppLogContext.info(CLASS, method, "Creating order",
                "userId", user.getId(),
                "planPriceId", request.getPlanPriceId(),
                "billingCycle", request.getBillingCycle());

        // ── Idempotency check ──────────────────────────────────────
        String idempotencyKey = request.getIdempotencyKey();
        if (orderRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            AppLogContext.warn(CLASS, method,
                    "Duplicate order attempt rejected",
                    "idempotencyKey", idempotencyKey,
                    "userId", user.getId());
            throw new BadRequestException(
                    "Order already exists for this session");
        }

        // ── Fetch and validate plan price ──────────────────────────
        PlanPrice planPrice;
        try {
            planPrice = planPriceRepository
                    .findById(UUID.fromString(request.getPlanPriceId()))
                    .filter(PlanPrice::isActive)
                    .orElseThrow(() -> new BadRequestException(
                            "Invalid or inactive plan price: "
                                    + request.getPlanPriceId()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "Invalid plan price ID format: " + request.getPlanPriceId());
        }

        Plan plan = planPrice.getPlan();

        // ── Block FREE plan payments ───────────────────────────────
        if (planPrice.getMonthlyPrice() == 0
                && planPrice.getAnnualPrice() == 0) {
            AppLogContext.warn(CLASS, method,
                    "Attempt to pay for FREE plan rejected",
                    "userId", user.getId());
            throw new BadRequestException("FREE plan does not require payment");
        }

        // ── Calculate amounts server-side ──────────────────────────
        AmountBreakdown amounts = calculateAmounts(
                planPrice, request.getBillingCycle(), null);

        // ── Apply coupon if provided ───────────────────────────────
        Coupon coupon = null;
        if (request.getCouponCode() != null
                && !request.getCouponCode().isBlank()) {
            coupon = validateCoupon(request.getCouponCode(), user.getId());
            amounts = applyDiscount(amounts, coupon, planPrice.getGstRate());
            AppLogContext.info(CLASS, method, "Coupon applied",
                    "code", coupon.getCode(),
                    "discount", amounts.discount(),
                    "finalTotal", amounts.total());
        }

        // ── Persist our order first ────────────────────────────────
        BillingOrder order = BillingOrder.builder()
                .user(user)
                .plan(plan)
                .planPrice(planPrice)
                .billingCycle(request.getBillingCycle())
                .gateway(planPrice.getGateway())
                .status("PENDING")
                .coupon(coupon)
                .currency(planPrice.getCurrency())
                .planPriceAmount(amounts.planPrice())
                .discountAmount(amounts.discount())
                .subtotal(amounts.subtotal())
                .gstRate(planPrice.getGstRate())
                .gstAmount(amounts.gstAmount())
                .totalAmount(amounts.total())
                .idempotencyKey(idempotencyKey)
                .countryCode(countryCode)
                .ipAddress(clientIp)
                .gstin(request.getGstin())
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();

        order = orderRepository.save(order);
        AppLogContext.info(CLASS, method, "Order persisted",
                "orderId", order.getId(),
                "amount", amounts.total(),
                "currency", planPrice.getCurrency());

        // ── Full discount coupon — skip gateway ────────────────────
        if (amounts.total() == 0) {
            AppLogContext.info(CLASS, method,
                    "Full discount coupon — activating without gateway",
                    "orderId", order.getId());
            return handleFullDiscountOrder(order, user, coupon);
        }

        // ── Create Razorpay order ──────────────────────────────────
        GatewayOrderResponse gatewayOrder;
        try {
            gatewayOrder = paymentGateway.createOrder(
                    GatewayOrderRequest.builder()
                            .amount(amounts.total())
                            .currency(planPrice.getCurrency())
                            .receiptId(order.getId().toString())
                            .idempotencyKey(idempotencyKey)
                            .build());
        } catch (GatewayException e) {
            // Mark order as failed if gateway call fails
            order.setStatus("FAILED");
            order.setFailureCode("GATEWAY_ERROR");
            order.setFailureDescription(e.getMessage());
            orderRepository.save(order);
            AppLogContext.error(CLASS, method,
                    "Gateway order creation failed",
                    e,
                    "orderId", order.getId());
            throw new GatewayException(
                    "Payment gateway unavailable. Please try again.", e);
        }

        order.setGatewayOrderId(gatewayOrder.getGatewayOrderId());
        orderRepository.save(order);

        AppLogContext.info(CLASS, method, "Order created successfully",
                "orderId", order.getId(),
                "gatewayOrderId", gatewayOrder.getGatewayOrderId(),
                "amount", amounts.total());

        return CreateOrderResponse.builder()
                .orderId(order.getId().toString())
                .gatewayOrderId(gatewayOrder.getGatewayOrderId())
                .amount(amounts.total())
                .currency(planPrice.getCurrency())
                .planName(plan.getDisplayName())
                .billingCycle(request.getBillingCycle())
                .breakdown(amounts)
                .build();
    }

    // ─── Verify Payment ────────────────────────────────────────────

    /**
     * Called after user completes payment in Razorpay SDK.
     * Verifies HMAC-SHA256 signature, checks captured amount matches order,
     * then activates the subscription.
     *
     * @throws ResourceNotFoundException if order not found
     * @throws BadRequestException       if signature or amount is invalid
     * @throws IllegalStateException     if order already processed
     */
    @Transactional
    public VerifyPaymentResponse verifyAndActivate(User user,
                                                   VerifyPaymentRequest request) {
        String method = "verifyAndActivate";
        AppLogContext.info(CLASS, method, "Verifying payment",
                "userId", user.getId(),
                "gatewayOrderId", request.getGatewayOrderId(),
                "gatewayPaymentId", request.getGatewayPaymentId());

        // ── Find our order ─────────────────────────────────────────
        BillingOrder order = orderRepository
                .findByGatewayOrderId(request.getGatewayOrderId())
                .orElseThrow(() -> {
                    AppLogContext.warn(CLASS, method,
                            "Order not found for gateway order",
                            "gatewayOrderId", request.getGatewayOrderId());
                    return new ResourceNotFoundException(
                            "Order", "gatewayOrderId",
                            request.getGatewayOrderId());
                });

        // ── Ownership check ────────────────────────────────────────
        if (!order.getUser().getId().equals(user.getId())) {
            AppLogContext.warn(CLASS, method,
                    "Order ownership mismatch — possible fraud attempt",
                    "orderId", order.getId(),
                    "orderUserId", order.getUser().getId(),
                    "requestUserId", user.getId());
            throw new BadRequestException("Order does not belong to user");
        }

        // ── Order must be PENDING ──────────────────────────────────
        if (!"PENDING".equals(order.getStatus())) {
            AppLogContext.warn(CLASS, method,
                    "Order already processed",
                    "orderId", order.getId(),
                    "status", order.getStatus());
            throw new BadRequestException(
                    "Order already processed: " + order.getStatus());
        }

        // ── Verify HMAC-SHA256 signature ───────────────────────────
        boolean signatureValid = paymentGateway.verifyPaymentSignature(
                request.getGatewayOrderId(),
                request.getGatewayPaymentId(),
                request.getSignature());

        if (!signatureValid) {
            order.setStatus("FAILED");
            order.setFailureCode("SIGNATURE_MISMATCH");
            order.setFailureDescription(
                    "Payment signature verification failed");
            orderRepository.save(order);
            AppLogContext.warn(CLASS, method,
                    "Signature verification failed — possible tampering",
                    "orderId", order.getId(),
                    "gatewayPaymentId", request.getGatewayPaymentId());
            throw new BadRequestException(
                    "Payment verification failed. Please contact support.");
        }

        // ── Fetch payment from Razorpay + verify amount ────────────
        GatewayPaymentDetails gatewayPayment;
        try {
            gatewayPayment = paymentGateway
                    .fetchPayment(request.getGatewayPaymentId());
        } catch (GatewayException e) {
            AppLogContext.error(CLASS, method,
                    "Failed to fetch payment from gateway",
                    e,
                    "gatewayPaymentId", request.getGatewayPaymentId());
            throw new GatewayException(
                    "Could not verify payment with gateway. Please contact support.", e);
        }

        // Amount tamper check — gateway amount must match our order
        if (gatewayPayment.getAmount() != order.getTotalAmount()) {
            order.setStatus("FAILED");
            order.setFailureCode("AMOUNT_MISMATCH");
            order.setFailureDescription(
                    "Gateway amount " + gatewayPayment.getAmount()
                            + " does not match order amount " + order.getTotalAmount());
            orderRepository.save(order);
            AppLogContext.error(CLASS, method,
                    "CRITICAL: Amount mismatch — possible fraud",
                    "orderId", order.getId(),
                    "expectedAmount", order.getTotalAmount(),
                    "gatewayAmount", gatewayPayment.getAmount());
            throw new BadRequestException(
                    "Payment amount mismatch. Please contact support.");
        }

        return activateSubscription(order, user, gatewayPayment,
                request.getGatewayPaymentId());
    }

    // ─── Activate Subscription ─────────────────────────────────────

    /**
     * Creates payment, subscription, invoice records and upgrades user plan.
     * Idempotent — safe to call multiple times for same payment.
     * Called by both verifyAndActivate() and WebhookController.
     */
    @Transactional
    public VerifyPaymentResponse activateSubscription(
            BillingOrder order,
            User user,
            GatewayPaymentDetails gatewayPayment,
            String gatewayPaymentId) {
        String method = "activateSubscription";

        // ── Idempotency — skip if already activated ────────────────
        if (paymentRepository.findByGatewayPaymentId(
                gatewayPaymentId).isPresent()) {
            AppLogContext.info(CLASS, method,
                    "Payment already processed — returning existing subscription",
                    "gatewayPaymentId", gatewayPaymentId);
            Subscription existing = subscriptionRepository
                    .findByUserIdAndStatus(user.getId(), "ACTIVE")
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Subscription", "userId", user.getId()));
            return VerifyPaymentResponse.builder()
                    .success(true)
                    .planName(existing.getPlan().getDisplayName())
                    .build();
        }

        // ── Create payment record ──────────────────────────────────
        Payment payment = Payment.builder()
                .user(user)
                .order(order)
                .gateway(order.getGateway())
                .gatewayOrderId(order.getGatewayOrderId())
                .gatewayPaymentId(gatewayPaymentId)
                .idempotencyKey(order.getIdempotencyKey())
                .status("CAPTURED")
                .currency(order.getCurrency())
                .subtotalAmount(order.getSubtotal())
                .discountAmount(order.getDiscountAmount())
                .gstRate(order.getGstRate())
                .gstAmount(order.getGstAmount())
                .totalAmount(order.getTotalAmount())
                .countryCode(order.getCountryCode())
                .paymentMethod(gatewayPayment.getMethod())
                .ipAddress(order.getIpAddress())
                .gstin(order.getGstin())
                .gatewayResponse(gatewayPayment.getRaw())
                .build();

        payment = paymentRepository.save(payment);
        AppLogContext.info(CLASS, method, "Payment record created",
                "paymentId", payment.getId(),
                "amount", payment.getTotalAmount(),
                "method", payment.getPaymentMethod());

        // ── Calculate subscription period ──────────────────────────
        Instant now = Instant.now();
        Instant end = "ANNUAL".equals(order.getBillingCycle())
                ? now.plus(365, ChronoUnit.DAYS)
                : now.plus(30, ChronoUnit.DAYS);

        // ── Create subscription ────────────────────────────────────
        Subscription subscription = Subscription.builder()
                .user(user)
                .plan(order.getPlan())
                .planPrice(order.getPlanPrice())
                .order(order)
                .gateway(order.getGateway())
                .status("ACTIVE")
                .billingCycle(order.getBillingCycle())
                .currentPeriodStart(now)
                .currentPeriodEnd(end)
                .coupon(order.getCoupon())
                .discountAmount(order.getDiscountAmount())
                .currency(order.getCurrency())
                .planPriceAmount(order.getPlanPriceAmount())
                .gstRate(order.getGstRate())
                .gstAmount(order.getGstAmount())
                .finalAmount(order.getTotalAmount())
                .build();

        subscription = subscriptionRepository.save(subscription);

        // ── Link back to order and payment ─────────────────────────
        order.setStatus("PAID");
        order.setSubscription(subscription);
        orderRepository.save(order);

        payment.setSubscription(subscription);
        paymentRepository.save(payment);

        // ── Redeem coupon atomically ───────────────────────────────
        if (order.getCoupon() != null) {
            redeemCoupon(order.getCoupon(), user.getId(), payment);
        }

        // ── Upgrade user plan ──────────────────────────────────────
        String previousPlan = user.getPlan().name();
        String newPlan = order.getPlan().getName();

        try {
            user.setPlan(com.cyopo.auth.model.Plan.valueOf(newPlan));
            userRepository.save(user);
        } catch (IllegalArgumentException e) {
            AppLogContext.error(CLASS, method,
                    "Invalid plan name — cannot upgrade user",
                    e, "planName", newPlan);
            throw new BadRequestException(
                    "Invalid plan configuration. Please contact support.");
        }

        // ── Generate invoice ───────────────────────────────────────
        Invoice invoice = generateInvoice(order, payment, subscription, user);

        // ── Audit log ──────────────────────────────────────────────
        logPlanChange(user, previousPlan, newPlan,
                "PAYMENT", subscription, payment, order);

        AppLogContext.info(CLASS, method,
                "Subscription activated successfully",
                "userId", user.getId(),
                "plan", newPlan,
                "billingCycle", order.getBillingCycle(),
                "periodEnd", end,
                "invoiceNumber", invoice.getInvoiceNumber());

        return VerifyPaymentResponse.builder()
                .success(true)
                .planName(order.getPlan().getDisplayName())
                .subscriptionId(subscription.getId().toString())
                .invoiceId(invoice.getId().toString())
                .periodEnd(end)
                .build();
    }

    // ─── Coupon Validation ─────────────────────────────────────────

    /**
     * Validates a coupon code against all business rules.
     * Does NOT redeem — redemption happens only after payment is confirmed.
     *
     * @throws BadRequestException if coupon is invalid, expired, or exhausted
     */
    public Coupon validateCoupon(String code, UUID userId) {
        String method = "validateCoupon";
        AppLogContext.info(CLASS, method, "Validating coupon",
                "code", code, "userId", userId);

        Coupon coupon = couponRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> {
                    AppLogContext.warn(CLASS, method,
                            "Coupon not found", "code", code);
                    return new BadRequestException(
                            "Invalid coupon code: " + code);
                });

        if (!coupon.getIsActive()) {
            AppLogContext.warn(CLASS, method,
                    "Inactive coupon used", "code", code);
            throw new BadRequestException("This coupon is no longer active");
        }

        Instant now = Instant.now();

        if (coupon.getValidFrom() != null
                && coupon.getValidFrom().isAfter(now.plusSeconds(60))) {
            AppLogContext.warn(CLASS, method,
                    "Coupon not yet valid",
                    "code", code,
                    "validFrom", coupon.getValidFrom());
            throw new BadRequestException("This coupon is not yet valid");
        }

        if (coupon.getValidUntil() != null
                && coupon.getValidUntil().isBefore(now)) {
            AppLogContext.warn(CLASS, method,
                    "Expired coupon used",
                    "code", code,
                    "validUntil", coupon.getValidUntil());
            throw new BadRequestException("This coupon has expired");
        }

        if (coupon.getMaxUses() != null
                && coupon.getUsedCount() >= coupon.getMaxUses()) {
            AppLogContext.warn(CLASS, method,
                    "Coupon fully redeemed",
                    "code", code,
                    "usedCount", coupon.getUsedCount(),
                    "maxUses", coupon.getMaxUses());
            throw new BadRequestException(
                    "This coupon has been fully redeemed");
        }

        AppLogContext.info(CLASS, method, "Coupon valid",
                "code", code,
                "discountType", coupon.getDiscountType(),
                "discountValue", coupon.getDiscountValue());

        return coupon;
    }

    // ─── Cancel Subscription ───────────────────────────────────────

    /**
     * Cancels subscription at period end (Option B).
     * User retains access until current_period_end.
     *
     * @throws ResourceNotFoundException if no active subscription found
     */
    @Transactional
    public void cancelSubscription(User user, String reason) {
        String method = "cancelSubscription";
        AppLogContext.info(CLASS, method, "Cancelling subscription",
                "userId", user.getId(), "reason", reason);

        Subscription subscription = subscriptionRepository
                .findByUserIdAndStatus(user.getId(), "ACTIVE")
                .orElseThrow(() -> {
                    AppLogContext.warn(CLASS, method,
                            "No active subscription found",
                            "userId", user.getId());
                    return new ResourceNotFoundException(
                            "Subscription", "userId", user.getId());
                });

        subscription.setCancelAtPeriodEnd(true);
        subscription.setCancelledAt(Instant.now());
        subscription.setCancellationReason(reason);
        subscriptionRepository.save(subscription);

        logPlanChange(user, user.getPlan().name(), user.getPlan().name(),
                "CANCELLATION", subscription, null, null);

        AppLogContext.info(CLASS, method,
                "Subscription cancelled — access retained until period end",
                "userId", user.getId(),
                "periodEnd", subscription.getCurrentPeriodEnd());
    }

    // ─── Get Active Subscription ───────────────────────────────────

    /**
     * Returns the user's active subscription mapped to SubscriptionResponse.
     * Maps inside @Transactional to avoid LazyInitializationException
     * on subscription.user and subscription.plan lazy proxies.
     */
    @Transactional(readOnly = true)
    public Optional<SubscriptionResponse> getActiveSubscription(UUID userId) {
        return subscriptionRepository
                .findByUserIdAndStatus(userId, "ACTIVE")
                .map(s -> new SubscriptionResponse(
                        s.getId().toString(),
                        s.getStatus(),
                        s.getBillingCycle(),
                        s.getCurrentPeriodStart() != null
                                ? s.getCurrentPeriodStart().toString() : null,
                        s.getCurrentPeriodEnd() != null
                                ? s.getCurrentPeriodEnd().toString() : null,
                        s.isCancelAtPeriodEnd(),
                        s.getCancelledAt() != null
                                ? s.getCancelledAt().toString() : null,
                        s.getCurrency(),
                        s.getFinalAmount(),
                        s.getGateway(),
                        s.getPlan().getName(),
                        s.getPlan().getDisplayName()
                ));
    }

    // ─── Record Refund ─────────────────────────────────────────────

    /**
     * Records a refund received via webhook.
     * Full refund → REFUNDED. Partial → PARTIALLY_REFUNDED.
     */
    @Transactional
    public void recordRefund(String gatewayPaymentId,
                             long refundAmount,
                             String refundGatewayId) {
        String method = "recordRefund";
        AppLogContext.info(CLASS, method, "Recording refund",
                "gatewayPaymentId", gatewayPaymentId,
                "refundAmount", refundAmount,
                "refundGatewayId", refundGatewayId);

        paymentRepository.findByGatewayPaymentId(gatewayPaymentId)
                .ifPresentOrElse(payment -> {
                    payment.setRefundAmount(refundAmount);
                    payment.setRefundGatewayId(refundGatewayId);
                    payment.setRefundedAt(Instant.now());
                    payment.setStatus(
                            refundAmount >= payment.getTotalAmount()
                                    ? "REFUNDED"
                                    : "PARTIALLY_REFUNDED");
                    paymentRepository.save(payment);
                    AppLogContext.info(CLASS, method,
                            "Refund recorded",
                            "paymentId", payment.getId(),
                            "status", payment.getStatus());
                }, () -> AppLogContext.warn(CLASS, method,
                        "Payment not found for refund",
                        "gatewayPaymentId", gatewayPaymentId));
    }

    // ─── Private Helpers ───────────────────────────────────────────

    private AmountBreakdown calculateAmounts(PlanPrice planPrice,
                                             String billingCycle,
                                             Long overridePrice) {
        long basePrice = overridePrice != null ? overridePrice
                : "ANNUAL".equals(billingCycle)
                ? planPrice.getAnnualPrice()
                : planPrice.getMonthlyPrice();

        long gstAmount = BigDecimal.valueOf(basePrice)
                .multiply(planPrice.getGstRate())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();

        return new AmountBreakdown(basePrice, 0L, basePrice,
                gstAmount, basePrice + gstAmount);
    }

    private AmountBreakdown applyDiscount(AmountBreakdown amounts,
                                          Coupon coupon,
                                          BigDecimal gstRate) {
        long discount = switch (coupon.getDiscountType().name()) {
            case "PERCENTAGE" -> amounts.planPrice() > 0
                    ? coupon.getDiscountValue()
                    .multiply(BigDecimal.valueOf(amounts.planPrice()))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValue()
                    : 0L;
            case "FIXED" -> Math.min(coupon.getDiscountValue().longValue(),
                    amounts.planPrice());
            case "FULL" -> amounts.planPrice();
            default -> 0L;
        };

        long subtotal = Math.max(0, amounts.planPrice() - discount);
        long gstAmount = BigDecimal.valueOf(subtotal)
                .multiply(gstRate)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();

        return new AmountBreakdown(amounts.planPrice(), discount,
                subtotal, gstAmount, subtotal + gstAmount);
    }

    private CreateOrderResponse handleFullDiscountOrder(BillingOrder order,
                                                        User user,
                                                        Coupon coupon) {
        GatewayPaymentDetails fakeDetails = GatewayPaymentDetails.builder()
                .gatewayPaymentId("COUPON_" + UUID.randomUUID())
                .amount(0L)
                .currency(order.getCurrency())
                .status("captured")
                .method("coupon")
                .build();

        activateSubscription(order, user, fakeDetails,
                fakeDetails.getGatewayPaymentId());

        return CreateOrderResponse.builder()
                .orderId(order.getId().toString())
                .gatewayOrderId(null)
                .amount(0L)
                .currency(order.getCurrency())
                .planName(order.getPlan().getDisplayName())
                .billingCycle(order.getBillingCycle())
                .breakdown(new AmountBreakdown(
                        order.getPlanPriceAmount(),
                        order.getDiscountAmount(),
                        order.getSubtotal(),
                        order.getGstAmount(),
                        0L))
                .build();
    }

    private void redeemCoupon(Coupon coupon, UUID userId, Payment payment) {
        String method = "redeemCoupon";
        int updated = couponRepository.incrementUsedCount(
                coupon.getId(), coupon.getMaxUses());
        if (updated == 0) {
            AppLogContext.warn(CLASS, method,
                    "Coupon race condition — could not redeem but subscription activated",
                    "code", coupon.getCode(),
                    "userId", userId);
        } else {
            AppLogContext.info(CLASS, method, "Coupon redeemed",
                    "code", coupon.getCode(), "userId", userId);
        }
    }

    private Invoice generateInvoice(BillingOrder order,
                                    Payment payment,
                                    Subscription subscription,
                                    User user) {
        String method = "generateInvoice";
        String invoiceNumber = invoiceRepository.generateInvoiceNumber();

        Invoice invoice = Invoice.builder()
                .user(user)
                .order(order)
                .payment(payment)
                .subscription(subscription)
                .invoiceNumber(invoiceNumber)
                .status("ISSUED")
                .currency(order.getCurrency())
                .subtotal(order.getSubtotal())
                .discount(order.getDiscountAmount())
                .gstRate(order.getGstRate())
                .gstAmount(order.getGstAmount())
                .total(order.getTotalAmount())
                .billingName(user.getName())
                .billingEmail(user.getEmail())
                .sellerGstin(sellerGstin.isBlank() ? null : sellerGstin)
                .gstin(order.getGstin())
                .periodStart(subscription.getCurrentPeriodStart())
                .periodEnd(subscription.getCurrentPeriodEnd())
                .issuedAt(Instant.now())
                .paidAt(Instant.now())
                .build();

        Invoice saved = invoiceRepository.save(invoice);

        // Generate PDF and upload to Cloudinary (non-critical — won't fail invoice)
        String pdfUrl = invoicePdfService.generateAndUpload(saved);
        if (pdfUrl != null) {
            saved.setPdfUrl(pdfUrl);
            invoiceRepository.save(saved);
        }
        AppLogContext.info(CLASS, "generateInvoice",
                "Invoice generated",
                "invoiceNumber", saved.getInvoiceNumber(),
                "total", saved.getTotal(),
                "pdfGenerated", pdfUrl != null);

        if (pdfUrl == null) {
            AppLogContext.warn(CLASS, "generateInvoice",
                    "Invoice PDF generation failed — pdfUrl is null",
                    "invoiceNumber", saved.getInvoiceNumber(),
                    "userId", user.getId());
        }
        return saved;
    }

    /**
     * Returns invoice history for a user ordered by most recent first.
     * Maps to InvoiceResponse inside @Transactional to avoid
     * LazyInitializationException on invoice.user lazy proxy.
     */
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getInvoices(UUID userId) {
        return invoiceRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(i -> new InvoiceResponse(
                        i.getId().toString(),
                        i.getInvoiceNumber(),
                        i.getStatus(),
                        i.getCurrency(),
                        i.getSubtotal(),
                        i.getDiscount(),
                        i.getGstRate(),
                        i.getGstAmount(),
                        i.getTotal(),
                        i.getBillingName(),
                        i.getBillingEmail(),
                        i.getPdfUrl(),
                        i.getPeriodStart() != null ? i.getPeriodStart().toString() : null,
                        i.getPeriodEnd() != null ? i.getPeriodEnd().toString() : null,
                        i.getIssuedAt() != null ? i.getIssuedAt().toString() : null,
                        i.getPaidAt() != null ? i.getPaidAt().toString() : null
                ))
                .toList();
    }

    private void logPlanChange(User user, String fromPlan, String toPlan,
                               String reason, Subscription subscription,
                               Payment payment, BillingOrder order) {
        planChangeLogRepository.save(PlanChangeLog.builder()
                .user(user)
                .fromPlan(fromPlan)
                .toPlan(toPlan)
                .reason(reason)
                .subscription(subscription)
                .payment(payment)
                .order(order)
                .build());
    }

    // ─── Records ───────────────────────────────────────────────────

    public record AmountBreakdown(
            long planPrice,
            long discount,
            long subtotal,
            long gstAmount,
            long total
    ) {
    }
}