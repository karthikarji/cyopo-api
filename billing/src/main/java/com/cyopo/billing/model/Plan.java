package com.cyopo.billing.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "plans", schema = "billing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // FREE | PREMIUM | PRO
    @Column(nullable = false, unique = true, length = 20)
    private String name;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    // ─── Hard limits ──────────────────────────────────────────────
    @Column(name = "max_portfolios", nullable = false)
    private int maxPortfolios;

    @Column(name = "max_projects_per_portfolio", nullable = false)
    private int maxProjectsPerPortfolio;

    @Column(name = "allow_custom_domain", nullable = false)
    private boolean allowCustomDomain;

    @Column(name = "allow_resume_upload", nullable = false)
    @Builder.Default
    private boolean allowResumeUpload = true;

    @Column(name = "allow_analytics", nullable = false)
    @Builder.Default
    private boolean allowAnalytics = true;

    @Column(name = "allow_premium_templates", nullable = false)
    private boolean allowPremiumTemplates;

    @Column(name = "allow_messages", nullable = false)
    private boolean allowMessages;

    @Column(name = "remove_branding", nullable = false)
    private boolean removeBranding;

    // ─── UI content ───────────────────────────────────────────────
    // Bullet points shown on pricing card — stored as JSON array
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> features = List.of();

    // "Most Popular" | "Best Value" | null
    @Column(length = 50)
    private String badge;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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