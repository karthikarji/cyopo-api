package com.cyopo.billing.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "webhook_events",
        schema = "billing",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_webhook_gateway_event",
                columnNames = {"gateway", "event_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // RAZORPAY | STRIPE
    @Column(nullable = false, length = 20)
    private String gateway;

    // Unique event ID from gateway — idempotency key
    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    // e.g. payment.captured | payment.failed | refund.created
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    // Full raw payload from gateway
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    // false until successfully processed
    @Column(nullable = false)
    @Builder.Default
    private boolean processed = false;

    @Column(name = "processed_at")
    private Instant processedAt;

    // Populated if processing threw an exception
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "last_retry_at")
    private Instant lastRetryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}