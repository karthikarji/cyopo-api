package com.cyopo.core.dto.request;

import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
public class UpdatePortfolioRequest {

    private String name;
    private UUID templateId;
    private CreatePortfolioRequest.ProfileRequest profile;
    private List<CreatePortfolioRequest.SkillRequest> skills;
    private List<CreatePortfolioRequest.CertificationRequest> certifications;
    private List<CreatePortfolioRequest.ExperienceRequest> experiences;
    private List<CreatePortfolioRequest.ProjectRequest> projects;
    private CreatePortfolioRequest.SettingsRequest settings;
    private String templateConfig;
}