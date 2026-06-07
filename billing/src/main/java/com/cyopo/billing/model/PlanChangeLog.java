package com.cyopo.billing.model;

import com.cyopo.auth.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plan_change_log", schema = "billing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // null on very first plan activation
    @Column(name = "from_plan", length = 20)
    private String fromPlan;

    @Column(name = "to_plan", nullable = false, length = 20)
    private String toPlan;

    // PAYMENT | EXPIRY | ADMIN | REFUND | COUPON | CANCELLATION
    @Column(nullable = false, length = 30)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private BillingOrder order;

    // null = system job | UUID = admin who manually changed
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant changedAt = Instant.now();
}