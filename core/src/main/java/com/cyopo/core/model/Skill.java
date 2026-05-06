package com.cyopo.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Skill {

    @Column(name = "name", length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50)
    private SkillCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "proficiency", length = 50)
    private SkillProficiency proficiency;

    @Column(name = "level")
    private Integer level;
}