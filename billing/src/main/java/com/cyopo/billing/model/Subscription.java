package com.cyopo.billing.model;

import com.cyopo.auth.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions", schema = "billing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

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
    @JoinColumn(name = "plan_price_id")
    private PlanPrice planPrice;

    // Which order created this subscription
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private BillingOrder order;

    // RAZORPAY | STRIPE
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String gateway = "RAZORPAY";

    // Razorpay subscription ID (for future recurring billing)
    @Column(name = "gateway_subscription_id", length = 100)
    private String gatewaySubscriptionId;

    // ACTIVE | CANCELLED | EXPIRED | PAST_DUE | PENDING
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    // MONTHLY | ANNUAL
    @Column(name = "billing_cycle", nullable = false, length = 10)
    @Builder.Default
    private String billingCycle = "MONTHLY";

    // ─── Billing period ───────────────────────────────────────────
    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    // ─── Cancellation (Option B — cancel at period end) ───────────
    // true = user cancelled, access continues until currentPeriodEnd
    @Column(name = "cancel_at_period_end", nullable = false)
    @Builder.Default
    private boolean cancelAtPeriodEnd = false;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    // ─── Coupon ───────────────────────────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    @Column(name = "discount_amount", nullable = false)
    @Builder.Default
    private long discountAmount = 0L;

    // ─── Price snapshot ───────────────────────────────────────────
    // Frozen at subscription creation — immune to future price changes
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "plan_price", nullable = false)
    @Builder.Default
    private long planPriceAmount = 0L;

    @Column(name = "gst_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal gstRate = BigDecimal.ZERO;

    @Column(name = "gst_amount", nullable = false)
    @Builder.Default
    private long gstAmount = 0L;

    // What the user was actually charged
    @Column(name = "final_amount", nullable = false)
    @Builder.Default
    private long finalAmount = 0L;

    // ─── Failed renewal tracking ──────────────────────────────────
    @Column(name = "grace_period_end")
    private Instant gracePeriodEnd;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "last_retry_at")
    private Instant lastRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}