package com.cyopo.core.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioProfile {

    @Column(name = "profile_name", length = 100)
    private String name;

    @Column(name = "profile_title", length = 200)
    private String title;

    @Column(name = "profile_bio", length = 1000)
    private String bio;

    @Column(name = "profile_email", length = 255)
    private String email;

    @Column(name = "profile_phone", length = 50)
    private String phone;

    @Column(name = "profile_location", length = 100)
    private String location;

    @Column(name = "profile_website", length = 500)
    private String website;

    @Column(name = "profile_photo", columnDefinition = "TEXT")
    private String profilePhoto;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_status", length = 20)
    @Builder.Default
    private ProfileStatus status = ProfileStatus.DRAFT;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "portfolio_social_links",
            schema = "portfolio",
            joinColumns = @JoinColumn(name = "portfolio_id")
    )
    @Builder.Default
    private List<SocialLink> socialMedia = new ArrayList<>();
}