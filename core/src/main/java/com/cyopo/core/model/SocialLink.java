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
public class SocialLink {

    @Column(name = "platform", length = 100)
    private String platform;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "username", length = 100)
    private String username;
}