package com.cyopo.core.service;

import com.cyopo.auth.model.Plan;
import com.cyopo.auth.repository.UserRepository;
import com.cyopo.common.exception.BadRequestException;
import com.cyopo.common.exception.ConflictException;
import com.cyopo.common.exception.PlanUpgradeRequiredException;
import com.cyopo.common.exception.ResourceNotFoundException;
import com.cyopo.common.response.PageResponse;
import com.cyopo.common.storage.StorageFolder;
import com.cyopo.common.storage.StorageResult;
import com.cyopo.common.storage.StorageService;
import com.cyopo.common.util.SlugUtil;
import com.cyopo.common.util.SanitizationUtil;
import com.cyopo.core.dto.request.CreatePortfolioRequest;
import com.cyopo.core.dto.request.PortfolioStatusRequest;
import com.cyopo.core.dto.request.UpdatePortfolioRequest;
import com.cyopo.core.dto.response.PortfolioResponse;
import com.cyopo.core.dto.response.SlugValidationResponse;
import com.cyopo.core.model.*;
import com.cyopo.core.repository.PortfolioRepository;
import com.cyopo.core.specification.PortfolioSpecification;
import com.cyopo.template.repository.TemplateRepository;
import com.cyopo.template.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final UserRepository userRepository;
    private final TemplateService templateService;
    private final StorageService storageService;

    // ─── User Operations ─────────────────────────────────────────────

    @Transactional
    public PortfolioResponse create(
            String userId,
            CreatePortfolioRequest request) {

        UUID userUUID = UUID.fromString(userId);

        String baseSlug = (request.getSlug() != null && !request.getSlug().isBlank())
                ? request.getSlug()
                : SlugUtil.generateSlug(request.getName());
        List<String> existingSlugs = portfolioRepository
                .findSlugsByPrefix(baseSlug);
        String slug = SlugUtil.nextAvailableSlug(baseSlug, existingSlugs);

        PortfolioProfile profile = buildProfile(request.getProfile());
        PortfolioSettings settings = buildSettings(request.getSettings());

        Portfolio portfolio = Portfolio.builder()
                .userId(userUUID)
                .templateId(request.getTemplateId())
                .templateSlug(resolveTemplateSlug(request.getTemplateId()))  // ← add
                .name(SanitizationUtil.sanitize(request.getName()))
                .slug(slug)
                .profile(profile)
                .settings(settings)
                .skills(buildSkills(request.getSkills()))
                .customSkillCategories(
                        request.getCustomSkillCategories() != null
                                ? request.getCustomSkillCategories()
                                : new ArrayList<>())
                .certifications(buildCertifications(request.getCertifications()))
                .build();

        Portfolio saved = portfolioRepository.save(portfolio);

        addExperiences(saved, request.getExperiences());
        addEducations(saved, request.getEducations());
        addProjects(saved, request.getProjects());

        Portfolio result = portfolioRepository.saveAndFlush(saved);
        log.info("Portfolio created: {} for user: {}", result.getSlug(), userId);
        String[] colors = resolveTemplateColors(result.getTemplateId());
        return PortfolioResponse.from(result, colors[0], colors[1]);
    }

    @Transactional(readOnly = true)
    public PageResponse<PortfolioResponse> getUserPortfolios(
            String userId,
            PortfolioStatus status,
            UUID templateId,
            int page,
            int limit) {

        UUID userUUID = UUID.fromString(userId);
        Specification<Portfolio> spec = Specification
                .where(PortfolioSpecification.hasUserId(userUUID));

        if (status != null) {
            spec = spec.and(PortfolioSpecification.hasStatus(status));
        }
        if (templateId != null) {
            spec = spec.and(PortfolioSpecification.hasTemplateId(templateId));
        }

        Pageable pageable = PageRequest.of(
                page - 1, limit,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Portfolio> result = portfolioRepository.findAll(spec, pageable);

        return new PageResponse<>(
                result.getContent().stream()
                        .map(PortfolioResponse::from)
                        .toList(),
                result.getTotalElements(),
                page,
                limit
        );
    }

    @Transactional(readOnly = true)
    public PortfolioResponse getById(String userId, UUID portfolioId) {
        Portfolio portfolio = portfolioRepository
                .findByIdAndUserId(portfolioId, UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "id", portfolioId));
        String[] colors = resolveTemplateColors(portfolio.getTemplateId());
        return PortfolioResponse.from(portfolio, colors[0], colors[1]);
    }

    @Transactional(readOnly = true)
    public PortfolioResponse previewBySlug(String userId, String slug) {
        Portfolio portfolio = portfolioRepository
                .findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "slug", slug));

        if (!portfolio.getUserId().equals(UUID.fromString(userId))) {
            throw new ResourceNotFoundException("Portfolio", "slug", slug);
        }
        String[] colors = resolveTemplateColors(portfolio.getTemplateId());
        return PortfolioResponse.from(portfolio, colors[0], colors[1]);
    }

    @Transactional
    public PortfolioResponse update(
            String userId,
            UUID portfolioId,
            UpdatePortfolioRequest request) {

        Portfolio portfolio = portfolioRepository
                .findByIdAndUserId(portfolioId, UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "id", portfolioId));

        if (request.getName() != null) {
            portfolio.setName(SanitizationUtil.sanitize(request.getName()));
        }
        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            if (!request.getSlug().equals(portfolio.getSlug())) {
                boolean slugExists = portfolioRepository
                        .existsBySlugAndIdNot(request.getSlug(), portfolioId);
                if (!slugExists) {
                    portfolio.setSlug(request.getSlug());
                }
            }
        }
        if (request.getTemplateId() != null) {
            portfolio.setTemplateId(request.getTemplateId());
            // ← Update slug when template changes
            portfolio.setTemplateSlug(
                    resolveTemplateSlug(request.getTemplateId()));
        }
        if (request.getProfile() != null) {
            // Delete old profile photo from Cloudinary if changing
            String oldPublicId = portfolio.getProfilePhotoPublicId();
            if (oldPublicId != null
                    && request.getProfile().getProfilePhoto() != null
                    && !request.getProfile().getProfilePhoto()
                    .equals(portfolio.getProfile().getProfilePhoto())) {
                storageService.delete(oldPublicId);
            }
            portfolio.setProfile(buildProfile(request.getProfile()));
        }
        if (request.getSettings() != null) {
            portfolio.setSettings(buildSettings(request.getSettings()));
        }
        if (request.getSkills() != null) {
            portfolio.setSkills(buildSkills(request.getSkills()));
        }
        if (request.getCustomSkillCategories() != null) {
            portfolio.setCustomSkillCategories(
                    request.getCustomSkillCategories());
        }
        if (request.getCertifications() != null) {
            portfolio.setCertifications(
                    buildCertifications(request.getCertifications()));
        }
        if (request.getExperiences() != null) {
            portfolio.getExperiences().clear();
            addExperiences(portfolio, request.getExperiences());
        }
        if (request.getEducations() != null) {
            portfolio.getEducations().clear();
            addEducations(portfolio, request.getEducations());
        }
        if (request.getProjects() != null) {
            portfolio.getProjects().clear();
            addProjects(portfolio, request.getProjects());
        }
        if (request.getTemplateConfig() != null) {
            portfolio.setTemplateConfig(request.getTemplateConfig());
        }

        Portfolio updated = portfolioRepository.saveAndFlush(portfolio);
        log.info("Portfolio updated: {}", updated.getSlug());
        return PortfolioResponse.from(updated);
    }

    @Transactional
    public PortfolioResponse updateStatus(
            String userId,
            UUID portfolioId,
            PortfolioStatusRequest request) {

        UUID userUUID = UUID.fromString(userId);

        Portfolio portfolio = portfolioRepository
                .findByIdAndUserId(portfolioId, userUUID)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "id", portfolioId));

        if (request.getStatus() == PortfolioStatus.PUBLISHED) {
            long publishedCount = portfolioRepository
                    .countByUserIdAndStatus(userUUID, PortfolioStatus.PUBLISHED);

            boolean isCurrentlyPublished =
                    portfolio.getStatus() == PortfolioStatus.PUBLISHED;

            if (publishedCount >= 1 && !isCurrentlyPublished) {
                userRepository.findById(userUUID).ifPresent(user -> {
                    if (user.getPlan() == Plan.FREE) {
                        throw new PlanUpgradeRequiredException(
                                "Upgrade to Premium to publish unlimited portfolios");
                    }
                });
            }
        }

        portfolio.setStatus(request.getStatus());
        Portfolio updated = portfolioRepository.saveAndFlush(portfolio);
        log.info("Portfolio status updated: {} → {}",
                updated.getSlug(), request.getStatus());
        return PortfolioResponse.from(updated);
    }

    @Transactional
    public void delete(String userId, UUID portfolioId) {
        Portfolio portfolio = portfolioRepository
                .findByIdAndUserId(portfolioId, UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "id", portfolioId));
        portfolioRepository.delete(portfolio);
        log.info("Portfolio deleted: {}", portfolio.getSlug());
    }

    @Transactional
    public PortfolioResponse duplicate(String userId, UUID portfolioId) {
        UUID userUUID = UUID.fromString(userId);
        Portfolio original = portfolioRepository
                .findByIdAndUserId(portfolioId, userUUID)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "id", portfolioId));

        String baseSlug = original.getSlug() + "-copy";
        List<String> existingSlugs = portfolioRepository
                .findSlugsByPrefix(baseSlug);
        String newSlug = SlugUtil.nextAvailableSlug(baseSlug, existingSlugs);

        PortfolioProfile originalProfile = original.getProfile();
        PortfolioProfile copiedProfile = new PortfolioProfile();
        copiedProfile.setName(originalProfile.getName());
        copiedProfile.setTitle(originalProfile.getTitle());
        copiedProfile.setBio(originalProfile.getBio());
        copiedProfile.setEmail(originalProfile.getEmail());
        copiedProfile.setPhone(originalProfile.getPhone());
        copiedProfile.setLocation(originalProfile.getLocation());
        copiedProfile.setWebsite(originalProfile.getWebsite());
        copiedProfile.setProfilePhoto(originalProfile.getProfilePhoto());
        copiedProfile.setStatus(originalProfile.getStatus());
        copiedProfile.setSocialMedia(new ArrayList<>(
                originalProfile.getSocialMedia()));

        PortfolioSettings originalSettings = original.getSettings();
        PortfolioSettings copiedSettings = PortfolioSettings.builder()
                .isPublic(originalSettings.getIsPublic())
                .allowComments(originalSettings.getAllowComments())
                .showContactInfo(originalSettings.getShowContactInfo())
                .showSkillLevels(originalSettings.getShowSkillLevels())
                .customDomain(originalSettings.getCustomDomain())
                .seoTitle(originalSettings.getSeoTitle())
                .seoDescription(originalSettings.getSeoDescription())
                .build();

        Portfolio duplicate = Portfolio.builder()
                .userId(userUUID)
                .templateId(original.getTemplateId())
                .templateSlug(original.getTemplateSlug())   // ← add
                .name(original.getName() + " (Copy)")
                .slug(newSlug)
                .status(PortfolioStatus.DRAFT)
                .profile(copiedProfile)
                .settings(copiedSettings)
                .skills(new ArrayList<>(original.getSkills()))
                .certifications(new ArrayList<>(original.getCertifications()))
                .templateConfig(original.getTemplateConfig())
                .build();

        Portfolio saved = portfolioRepository.saveAndFlush(duplicate);
        log.info("Portfolio duplicated: {} → {}",
                original.getSlug(), saved.getSlug());
        return PortfolioResponse.from(saved);
    }

    // ─── Public Operations ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<PortfolioResponse> getPublicPortfolios(
            String plan, int page, int limit) {

        Specification<Portfolio> spec = Specification
                .where(PortfolioSpecification.hasStatus(PortfolioStatus.PUBLISHED))
                .and(PortfolioSpecification.isPublic());

        Pageable pageable = PageRequest.of(
                page - 1, limit,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Portfolio> result = portfolioRepository.findAll(spec, pageable);

        return new PageResponse<>(
                result.getContent().stream()
                        .map(PortfolioResponse::from)
                        .toList(),
                result.getTotalElements(),
                page,
                limit
        );
    }

    @Transactional(readOnly = true)
    public PortfolioResponse getPublicBySlug(String slug) {
        Portfolio portfolio = portfolioRepository
                .findBySlugAndStatusAndSettingsIsPublic(
                        slug, PortfolioStatus.PUBLISHED, true)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "slug", slug));
        String[] colors = resolveTemplateColors(portfolio.getTemplateId());
        return PortfolioResponse.from(portfolio, colors[0], colors[1]);
    }

    @Transactional(readOnly = true)
    public SlugValidationResponse validateSlug(String slug, UUID excludeId) {
        boolean exists = excludeId != null
                ? portfolioRepository.existsBySlugAndIdNot(slug, excludeId)
                : portfolioRepository.existsBySlug(slug);

        return new SlugValidationResponse(
                !exists,
                exists ? "This slug is already taken" : "Slug is available"
        );
    }

    // ─── Profile Photo ────────────────────────────────────────────────

    @Transactional
    public String uploadProfilePhoto(String userId, UUID portfolioId,
                                     MultipartFile file) {
        Portfolio portfolio = portfolioRepository
                .findByIdAndUserId(portfolioId, UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "id", portfolioId));

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new BadRequestException("File size exceeds 2MB limit");
        }
        if (!isImageType(file.getContentType())) {
            throw new BadRequestException(
                    "Only image files are allowed (JPG, PNG, WebP, GIF)");
        }

        // Delete old photo from Cloudinary if exists
        if (portfolio.getProfilePhotoPublicId() != null) {
            storageService.delete(portfolio.getProfilePhotoPublicId());
        }

        StorageResult result = storageService.upload(file, StorageFolder.PROFILES);

        portfolio.getProfile().setProfilePhoto(result.url());
        portfolio.setProfilePhotoPublicId(result.publicId());
        portfolioRepository.save(portfolio);

        log.info("Profile photo uploaded for portfolio: {}", portfolioId);
        return result.url();
    }

    @Transactional
    public void deleteProfilePhoto(String userId, UUID portfolioId) {
        Portfolio portfolio = portfolioRepository
                .findByIdAndUserId(portfolioId, UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "id", portfolioId));

        if (portfolio.getProfilePhotoPublicId() != null) {
            storageService.delete(portfolio.getProfilePhotoPublicId());
            portfolio.getProfile().setProfilePhoto(null);
            portfolio.setProfilePhotoPublicId(null);
            portfolioRepository.save(portfolio);
        }

        log.info("Profile photo deleted for portfolio: {}", portfolioId);
    }

    // ─── Private helper ───────────────────────────────────────────────

    private PortfolioProfile buildProfile(
            CreatePortfolioRequest.ProfileRequest req) {
        if (req == null) return new PortfolioProfile();

        PortfolioProfile profile = new PortfolioProfile();
        profile.setName(SanitizationUtil.sanitize(req.getName()));
        profile.setTitle(SanitizationUtil.sanitize(req.getTitle()));
        profile.setBio(SanitizationUtil.sanitize(req.getBio()));
        profile.setEmail(req.getEmail());
        profile.setPhone(req.getPhone());
        profile.setLocation(req.getLocation());
        profile.setWebsite(req.getWebsite());
        profile.setProfilePhoto(req.getProfilePhoto());

        if (req.getSocialMedia() != null) {
            profile.setSocialMedia(new ArrayList<>(
                    req.getSocialMedia().stream()
                            .map(s -> SocialLink.builder()
                                    .platform(s.getPlatform())
                                    .url(s.getUrl())
                                    .username(s.getUsername())
                                    .build())
                            .toList()));
        }
        return profile;
    }

    private PortfolioSettings buildSettings(
            CreatePortfolioRequest.SettingsRequest req) {
        if (req == null) return new PortfolioSettings();

        return PortfolioSettings.builder()
                .isPublic(req.getIsPublic() != null
                        ? req.getIsPublic() : false)
                .allowComments(req.getAllowComments() != null
                        ? req.getAllowComments() : true)
                .showContactInfo(req.getShowContactInfo() != null
                        ? req.getShowContactInfo() : true)
                .showSkillLevels(req.getShowSkillLevels() != null
                        ? req.getShowSkillLevels() : true)
                .customDomain(req.getCustomDomain())
                .seoTitle(req.getSeoTitle())
                .seoDescription(req.getSeoDescription())
                .build();
    }

    private List<Skill> buildSkills(
            List<CreatePortfolioRequest.SkillRequest> reqs) {
        if (reqs == null) return new ArrayList<>();
        return new ArrayList<>(reqs.stream()
                .map(s -> Skill.builder()
                        .name(s.getName())
                        .category(s.getCategory())
                        .customCategory(s.getCustomCategory())
                        .proficiency(s.getProficiency())
                        .level(s.getLevel())
                        .build())
                .toList());
    }

    private List<Certification> buildCertifications(
            List<CreatePortfolioRequest.CertificationRequest> reqs) {
        if (reqs == null) return new ArrayList<>();
        return new ArrayList<>(reqs.stream()
                .map(c -> Certification.builder()
                        .name(c.getName())
                        .provider(c.getProvider())
                        .issueDate(c.getIssueDate() != null && !c.getIssueDate().isBlank()
                                ? LocalDate.parse(c.getIssueDate())
                                : null)
                        .expiryDate(c.getExpiryDate() != null && !c.getExpiryDate().isBlank()
                                ? LocalDate.parse(c.getExpiryDate())
                                : null)
                        .credentialId(c.getCredentialId())
                        .credentialUrl(c.getCredentialUrl())
                        .build())
                .toList());
    }

    private void addExperiences(Portfolio portfolio,
                                List<CreatePortfolioRequest.ExperienceRequest> reqs) {
        if (reqs == null) return;
        reqs.forEach(e -> {
            Experience exp = Experience.builder()
                    .portfolio(portfolio)
                    .title(e.getTitle())
                    .company(e.getCompany())
                    .location(e.getLocation())
                    .startDate(e.getStartDate() != null && !e.getStartDate().isBlank()
                            ? LocalDate.parse(e.getStartDate())
                            : null)
                    .endDate(e.getEndDate() != null && !e.getEndDate().isBlank()
                            ? LocalDate.parse(e.getEndDate())
                            : null)
                    .isCurrent(e.getIsCurrent() != null
                            ? e.getIsCurrent() : false)
                    .description(e.getDescription())
                    .achievements(new ArrayList<>(e.getAchievements()))
                    .technologies(new ArrayList<>(e.getTechnologies()))
                    .type(e.getType() != null
                            ? e.getType() : ExperienceType.FULL_TIME)
                    .build();
            portfolio.getExperiences().add(exp);
        });
    }

    private void addEducations(Portfolio portfolio,
                               List<CreatePortfolioRequest.EducationRequest> reqs) {
        if (reqs == null) return;
        for (int i = 0; i < reqs.size(); i++) {
            CreatePortfolioRequest.EducationRequest e = reqs.get(i);
            Education edu = Education.builder()
                    .portfolio(portfolio)
                    .institution(e.getInstitution())
                    .degree(e.getDegree())
                    .field(e.getField())
                    .startDate(e.getStartDate())
                    .endDate(e.getEndDate())
                    .isCurrent(e.getIsCurrent() != null ? e.getIsCurrent() : false)
                    .grade(e.getGrade())
                    .description(e.getDescription())
                    .sortOrder(i)
                    .build();
            portfolio.getEducations().add(edu);
        }
    }

    private void addProjects(Portfolio portfolio,
                             List<CreatePortfolioRequest.ProjectRequest> reqs) {
        if (reqs == null) return;
        reqs.forEach(p -> {
            Project proj = Project.builder()
                    .portfolio(portfolio)
                    .title(p.getTitle())
                    .description(p.getDescription())
                    .thumbnail(p.getThumbnail())
                    .demoUrl(p.getDemoUrl())
                    .githubUrl(p.getGithubUrl())
                    .isFeatured(p.getIsFeatured() != null
                            ? p.getIsFeatured() : false)
                    .completedDate(p.getCompletedDate() != null && !p.getCompletedDate().isBlank()
                            ? LocalDate.parse(p.getCompletedDate())
                            : null)
                    .technologies(new ArrayList<>(p.getTechnologies()))
                    .build();
            portfolio.getProjects().add(proj);
        });
    }

    private String resolveTemplateSlug(UUID templateId) {
        if (templateId == null) return null;
        return templateService.getTemplateSlug(templateId);
    }

    private boolean isImageType(String mimeType) {
        return mimeType != null && (
                mimeType.equals("image/jpeg") ||
                        mimeType.equals("image/png") ||
                        mimeType.equals("image/webp") ||
                        mimeType.equals("image/gif")
        );
    }

    private String[] resolveTemplateColors(UUID templateId) {
        return templateService.getTemplateColors(templateId);
    }
}