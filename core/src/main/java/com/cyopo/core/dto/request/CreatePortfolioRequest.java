package com.cyopo.core.dto.request;

import com.cyopo.core.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class CreatePortfolioRequest {

    @NotBlank(message = "Portfolio name is required")
    private String name;

    @NotNull(message = "Template ID is required")
    private UUID templateId;

    @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers and hyphens")
    private String slug;

    @Valid
    @NotNull(message = "Profile is required")
    private ProfileRequest profile;

    private List<SkillRequest> skills = new ArrayList<>();
    private List<CertificationRequest> certifications = new ArrayList<>();
    private List<ExperienceRequest> experiences = new ArrayList<>();
    private List<ProjectRequest> projects = new ArrayList<>();
    private SettingsRequest settings = new SettingsRequest();

    @Getter
    public static class ProfileRequest {

        @NotBlank(message = "Name is required")
        @Size(max = 100)
        private String name;

        @NotBlank(message = "Title is required")
        @Size(max = 200)
        private String title;

        @Size(max = 1000)
        private String bio;

        @NotBlank(message = "Email is required")
        private String email;

        private String phone;
        private String location;
        private String website;
        private String profilePhoto;

        @Size(max = 5, message = "Maximum 5 social media links")
        private List<SocialLinkRequest> socialMedia = new ArrayList<>();
    }

    @Getter
    public static class SocialLinkRequest {
        private String platform;
        private String url;
        private String username;
    }

    @Getter
    public static class SkillRequest {
        private String name;
        private SkillCategory category;
        private SkillProficiency proficiency;
        private Integer level;
    }

    @Getter
    public static class CertificationRequest {
        private String name;
        private String provider;
        private String issueDate;
        private String expiryDate;
        private String credentialId;
        private String credentialUrl;
    }

    @Getter
    public static class ExperienceRequest {
        @NotBlank(message = "Job title is required")
        private String title;

        @NotBlank(message = "Company is required")
        private String company;

        private String location;
        private String startDate;
        private String endDate;
        private Boolean isCurrent = false;

        @NotBlank(message = "Description is required")
        private String description;

        private List<String> achievements = new ArrayList<>();
        private List<String> technologies = new ArrayList<>();
    }

    @Getter
    public static class ProjectRequest {
        @NotBlank(message = "Project title is required")
        private String title;

        @NotBlank(message = "Project description is required")
        private String description;

        private String thumbnail;

        @Pattern(regexp = "^(https?://.*)?$",
                message = "Demo URL must be valid")
        private String demoUrl;

        @Pattern(regexp = "^(https?://.*)?$",
                message = "GitHub URL must be valid")
        private String githubUrl;

        private Boolean isFeatured = false;
        private String completedDate;
        private List<String> technologies = new ArrayList<>();
    }

    @Getter
    public static class SettingsRequest {
        private Boolean isPublic = false;
        private Boolean allowComments = true;
        private Boolean showContactInfo = true;
        private Boolean showSkillLevels = true;
        private String customDomain;
        private String seoTitle;
        private String seoDescription;
    }
}