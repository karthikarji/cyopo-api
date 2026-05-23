package com.cyopo.auth.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", schema = "auth")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 255)
    private String password;

    @Column(length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Plan plan = Plan.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "upgraded_at")
    private Instant upgradedAt;

    // Subscription fields (embedded directly in users table)
    @Column(name = "subscription_status", length = 20)
    private String subscriptionStatus;

    @Column(name = "subscription_plan", length = 20)
    private String subscriptionPlan;

    @Column(name = "subscription_period_start")
    private Instant subscriptionPeriodStart;

    @Column(name = "subscription_period_end")
    private Instant subscriptionPeriodEnd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Type(io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
    @Column(
            name = "notification_preferences",
            columnDefinition = "jsonb",
            nullable = false
    )
    @Builder.Default
    private NotificationPreferences notificationPreferences =
            new NotificationPreferences();

    public boolean isPremium() {
        return this.plan == Plan.PREMIUM;
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }


}