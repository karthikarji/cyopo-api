package com.cyopo.billing.model;

import com.cyopo.auth.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Named BillingOrder to avoid conflict with any existing Order class
@Entity
@Table(name = "orders", schema = "billing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_price_id", nullable = false)
    private PlanPrice planPrice;

    // MONTHLY | ANNUAL
    @Column(name = "billing_cycle", nullable = false, length = 10)
    @Builder.Default
    private String billingCycle = "MONTHLY";

    // RAZORPAY | STRIPE
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String gateway = "RAZORPAY";

    // Razorpay order_id — set after gateway order creation
    @Column(name = "gateway_order_id", unique = true, length = 100)
    private String gatewayOrderId;

    // PENDING | PAID | FAILED | EXPIRED | CANCELLED
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    // INR | USD | GBP
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    // All amounts in smallest unit (paise for INR)
    @Column(name = "plan_price", nullable = false)
    @Builder.Default
    private long planPriceAmount = 0L;

    @Column(name = "discount_amount", nullable = false)
    @Builder.Default
    private long discountAmount = 0L;

    @Column(name = "subtotal", nullable = false)
    @Builder.Default
    private long subtotal = 0L;

    @Column(name = "gst_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal gstRate = BigDecimal.ZERO;

    @Column(name = "gst_amount", nullable = false)
    @Builder.Default
    private long gstAmount = 0L;

    @Column(name = "total_amount", nullable = false)
    @Builder.Default
    private long totalAmount = 0L;

    // Prevents duplicate orders for the same checkout session
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "country_code", length = 3)
    private String countryCode;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    // Buyer's GSTIN — optional, for B2B invoices
    @Column(length = 15)
    private String gstin;

    // Set after successful payment + subscription activation
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    // PENDING orders auto-expire after 15 minutes
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_description", columnDefinition = "TEXT")
    private String failureDescription;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}