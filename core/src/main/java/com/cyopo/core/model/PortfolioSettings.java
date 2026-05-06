package com.cyopo.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioSettings {

    @Column(name = "settings_is_public")
    @Builder.Default
    private Boolean isPublic = false;

    @Column(name = "settings_allow_comments")
    @Builder.Default
    private Boolean allowComments = true;

    @Column(name = "settings_show_contact_info")
    @Builder.Default
    private Boolean showContactInfo = true;

    @Column(name = "settings_custom_domain", length = 255)
    private String customDomain;

    @Column(name = "settings_seo_title", length = 60)
    private String seoTitle;

    @Column(name = "settings_seo_description", length = 160)
    private String seoDescription;
}