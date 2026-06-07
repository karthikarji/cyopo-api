package com.cyopo.billing.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plan_prices", schema = "billing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    // INR | USD | GBP | EUR
    @Column(nullable = false, length = 3)
    private String currency;

    // RAZORPAY | STRIPE
    @Column(nullable = false, length = 20)
    private String gateway;

    // Prices in smallest unit — INR paise, USD cents
    @Column(name = "monthly_price", nullable = false)
    @Builder.Default
    private long monthlyPrice = 0L;

    @Column(name = "annual_price", nullable = false)
    @Builder.Default
    private long annualPrice = 0L;

    // 18.00 for INR, 0.00 for others
    @Column(name = "gst_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal gstRate = BigDecimal.ZERO;

    // false = currency not yet live (e.g. Stripe rows until Stripe is integrated)
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}