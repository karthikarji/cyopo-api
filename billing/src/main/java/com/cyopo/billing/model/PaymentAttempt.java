package com.cyopo.billing.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts", schema = "billing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private BillingOrder order;

    // Razorpay attempt payment_id
    @Column(name = "gateway_payment_id", length = 100)
    private String gatewayPaymentId;

    // Amount in smallest unit
    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    // ATTEMPTED | CAPTURED | FAILED
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_description", columnDefinition = "TEXT")
    private String failureDescription;

    // upi | card | netbanking
    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    // Full Razorpay response for this specific attempt
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gateway_response", columnDefinition = "jsonb")
    private Map<String, Object> gatewayResponse;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant attemptedAt = Instant.now();
}