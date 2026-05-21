package com.cyopo.core.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_photos", schema = "portfolio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "public_id", nullable = false, length = 255)
    private String publicId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size")
    private Integer fileSize;

    @Column(name = "is_thumbnail", nullable = false)
    @Builder.Default
    private Boolean isThumbnail = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;
}