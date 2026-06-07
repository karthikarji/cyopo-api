package com.cyopo.billing.model;

import com.cyopo.auth.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "payments", schema = "billing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private BillingOrder order;

    // Set after subscription is created
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    // RAZORPAY | STRIPE
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String gateway = "RAZORPAY";

    // Razorpay order_id
    @Column(name = "gateway_order_id", unique = true, length = 100)
    private String gatewayOrderId;

    // Razorpay payment_id — set after capture
    @Column(name = "gateway_payment_id", unique = true, length = 100)
    private String gatewayPaymentId;

    // Prevents double processing of same payment
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    // CREATED | ATTEMPTED | CAPTURED | FAILED | EXPIRED | REFUNDED | PARTIALLY_REFUNDED
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "CREATED";

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    // All amounts in smallest unit (paise for INR)
    @Column(name = "subtotal_amount", nullable = false)
    @Builder.Default
    private long subtotalAmount = 0L;

    @Column(name = "discount_amount", nullable = false)
    @Builder.Default
    private long discountAmount = 0L;

    @Column(name = "gst_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal gstRate = BigDecimal.ZERO;

    @Column(name = "gst_amount", nullable = false)
    @Builder.Default
    private long gstAmount = 0L;

    @Column(name = "total_amount", nullable = false)
    @Builder.Default
    private long totalAmount = 0L;

    @Column(name = "refund_amount", nullable = false)
    @Builder.Default
    private long refundAmount = 0L;

    // Buyer's GSTIN — optional
    @Column(length = 15)
    private String gstin;

    // ─── Failure tracking ─────────────────────────────────────────
    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_description", columnDefinition = "TEXT")
    private String failureDescription;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "last_retry_at")
    private Instant lastRetryAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    // ─── Location ─────────────────────────────────────────────────
    @Column(name = "country_code", length = 3)
    private String countryCode;

    // upi | card | netbanking | wallet
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    // Masked after 30 days (GDPR)
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "ip_masked_at")
    private Instant ipMaskedAt;

    // ─── Refund ───────────────────────────────────────────────────
    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refund_gateway_id", length = 100)
    private String refundGatewayId;

    @Column(name = "refund_reason", columnDefinition = "TEXT")
    private String refundReason;

    // Full Razorpay response — for debugging + reconciliation
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gateway_response", columnDefinition = "jsonb")
    private Map<String, Object> gatewayResponse;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}