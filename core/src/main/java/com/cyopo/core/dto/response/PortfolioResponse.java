package com.cyopo.core.dto.response;

import com.cyopo.core.model.*;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PortfolioResponse {

    private UUID   id;
    private UUID   userId;
    private UUID   templateId;
    private String name;
    private String slug;
    private PortfolioStatus  status;
    private PortfolioProfile profile;
    private PortfolioSettings settings;
    private List<Skill>          skills;
    private List<Certification>  certifications;
    private List<ExperienceResponse> experiences;
    private List<EducationResponse> educations;
    private List<ProjectResponse>    projects;
    private String  templateConfig;
    private String templateSlug;

    private String  resumeFileName;
    private Integer resumeFileSize;
    private boolean hasResume;

    private Instant createdAt;
    private Instant updatedAt;

    @Getter
    @Builder
    public static class ExperienceResponse {
        private UUID   id;
        private String title;
        private String company;
        private String location;
        private String startDate;
        private String endDate;
        private Boolean isCurrent;
        private String  description;
        private List<String> achievements;
        private List<String> technologies;
    }

    @Getter
    @Builder
    public static class EducationResponse {
        private UUID    id;
        private String  institution;
        private String  degree;
        private String  field;
        private String  startDate;
        private String  endDate;
        private Boolean isCurrent;
        private String  grade;
        private String  description;
        private Integer sortOrder;
    }

    @Getter
    @Builder
    public static class ProjectResponse {
        private UUID   id;
        private String title;
        private String description;
        private String thumbnail;
        private String demoUrl;
        private String githubUrl;
        private Boolean isFeatured;
        private String  completedDate;
        private List<String> technologies;
        private String templateSlug;
    }

    public static PortfolioResponse from(Portfolio portfolio) {
        return PortfolioResponse.builder()
                .id(portfolio.getId())
                .userId(portfolio.getUserId())
                .templateId(portfolio.getTemplateId())
                .name(portfolio.getName())
                .slug(portfolio.getSlug())
                .status(portfolio.getStatus())
                .profile(portfolio.getProfile())
                .settings(portfolio.getSettings())
                .skills(portfolio.getSkills())
                .certifications(portfolio.getCertifications())

                // ← Resume fields mapped from portfolio entity
                .resumeFileName(portfolio.getResumeFileName())
                .resumeFileSize(portfolio.getResumeFileSize())
                .hasResume(portfolio.getResumeFileName() != null)

                .experiences(portfolio.getExperiences().stream()
                        .map(e -> ExperienceResponse.builder()
                                .id(e.getId())
                                .title(e.getTitle())
                                .company(e.getCompany())
                                .location(e.getLocation())
                                .startDate(e.getStartDate() != null
                                        ? e.getStartDate().toString()
                                        : null)
                                .endDate(e.getEndDate() != null
                                        ? e.getEndDate().toString()
                                        : null)
                                .isCurrent(e.getIsCurrent())
                                .description(e.getDescription())
                                .achievements(e.getAchievements())
                                .technologies(e.getTechnologies())
                                .build())
                        .toList())
                .educations(portfolio.getEducations().stream()
                        .map(e -> EducationResponse.builder()
                                .id(e.getId())
                                .institution(e.getInstitution())
                                .degree(e.getDegree())
                                .field(e.getField())
                                .startDate(e.getStartDate())
                                .endDate(e.getEndDate())
                                .isCurrent(e.getIsCurrent())
                                .grade(e.getGrade())
                                .description(e.getDescription())
                                .sortOrder(e.getSortOrder())
                                .build())
                        .toList())
                .projects(portfolio.getProjects().stream()
                        .map(p -> ProjectResponse.builder()
                                .id(p.getId())
                                .title(p.getTitle())
                                .description(p.getDescription())
                                .thumbnail(p.getThumbnail())
                                .demoUrl(p.getDemoUrl())
                                .githubUrl(p.getGithubUrl())
                                .isFeatured(p.getIsFeatured())
                                .completedDate(p.getCompletedDate() != null
                                        ? p.getCompletedDate().toString()
                                        : null)
                                .technologies(p.getTechnologies())
                                .build())
                        .toList())
                .templateSlug(portfolio.getTemplateSlug())
                .templateConfig(portfolio.getTemplateConfig())
                .createdAt(portfolio.getCreatedAt())
                .updatedAt(portfolio.getUpdatedAt())
                .build();
    }
}