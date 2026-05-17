package com.cyopo.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "portfolios", schema = "portfolio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PortfolioStatus status = PortfolioStatus.DRAFT;

    @Column(name = "view_count")
    @Builder.Default
    private Long viewCount = 0L;

    @Embedded
    @Builder.Default
    private PortfolioProfile profile = new PortfolioProfile();

    @Embedded
    @Builder.Default
    private PortfolioSettings settings = new PortfolioSettings();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "portfolio_skills",
            schema = "portfolio",
            joinColumns = @JoinColumn(name = "portfolio_id")
    )
    @Builder.Default
    private List<Skill> skills = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "portfolio_certifications",
            schema = "portfolio",
            joinColumns = @JoinColumn(name = "portfolio_id")
    )
    @Builder.Default
    private List<Certification> certifications = new ArrayList<>();

    @OneToMany(
            mappedBy = "portfolio",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER
    )
    @Builder.Default
    private List<Experience> experiences = new ArrayList<>();

    @OneToMany(
            mappedBy = "portfolio",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER
    )
    @Builder.Default
    private List<Project> projects = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_config", columnDefinition = "jsonb")
    private String templateConfig;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resume_file_name", length = 255)
    private String resumeFileName;

    @Column(name = "resume_file_size")
    private Integer resumeFileSize;

    @Column(name = "template_slug", length = 10)
    private String templateSlug;
}