package com.cyopo.template.service;

import com.cyopo.common.exception.BadRequestException;
import com.cyopo.common.exception.ConflictException;
import com.cyopo.common.exception.ResourceNotFoundException;
import com.cyopo.common.response.PageResponse;
import com.cyopo.common.storage.StorageFolder;
import com.cyopo.common.storage.StorageResult;
import com.cyopo.common.storage.StorageService;
import com.cyopo.template.dto.request.CreateTemplateRequest;
import com.cyopo.template.dto.request.UpdateTemplateRequest;
import com.cyopo.template.dto.response.TemplateResponse;
import com.cyopo.template.model.Template;
import com.cyopo.template.model.TemplateStatus;
import com.cyopo.template.repository.TemplateRepository;
import com.cyopo.template.specification.TemplateSpecification;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final StorageService storageService;

    // ─── Admin Use Cases ────────────────────────────────────────────

    @Transactional
    public TemplateResponse create(CreateTemplateRequest request) {
        if (templateRepository.existsByTitle(
                request.getTitle().toLowerCase())) {
            throw new ConflictException(
                    "A template with this title already exists");
        }

        Template template = Template.builder()
                .title(request.getTitle().toLowerCase())
                .description(request.getDescription().toLowerCase())
                .font(request.getFont())
                .primaryColor(request.getPrimaryColor())
                .secondaryColor(request.getSecondaryColor())
                .premium(request.getPremium())
                .status(request.getStatus())
                .tags(new ArrayList<>(request.getTags().stream()
                        .map(String::toLowerCase)
                        .toList()))
                .build();

        Template saved = templateRepository.saveAndFlush(template);
        log.info("Template created: {}", saved.getTitle());
        return TemplateResponse.from(saved);
    }

    @Transactional
    public TemplateResponse update(UUID id,
                                   UpdateTemplateRequest request) {
        Template template = findTemplateById(id);

        if (request.getTitle() != null) {
            String newTitle = request.getTitle().toLowerCase();
            if (templateRepository.existsByTitleAndIdNot(newTitle, id)) {
                throw new ConflictException(
                        "A template with this title already exists");
            }
            template.setTitle(newTitle);
        }

        if (request.getDescription() != null) {
            template.setDescription(
                    request.getDescription().toLowerCase());
        }
        if (request.getFont() != null) {
            template.setFont(request.getFont());
        }
        if (request.getPrimaryColor() != null) {
            template.setPrimaryColor(request.getPrimaryColor());
        }
        if (request.getSecondaryColor() != null) {
            template.setSecondaryColor(request.getSecondaryColor());
        }
        if (request.getPremium() != null) {
            template.setPremium(request.getPremium());
        }
        if (request.getStatus() != null) {
            template.setStatus(request.getStatus());
        }
        if (request.getTags() != null) {
            template.setTags(new ArrayList<>(request.getTags().stream()
                    .map(String::toLowerCase)
                    .toList()));
        }

        Template updated = templateRepository.saveAndFlush(template);
        log.info("Template updated: {}", updated.getTitle());
        return TemplateResponse.from(updated);
    }

    @Transactional
    public void delete(UUID id) {
        Template template = findTemplateById(id);
        templateRepository.delete(template);
        log.info("Template deleted: {}", template.getTitle());
    }

    @Transactional
    public TemplateResponse duplicate(UUID id) {
        Template original = findTemplateById(id);

        // Find next available title
        // e.g. "minimal" → "minimal (copy)" → "minimal (copy 2)"
        String baseTitle = original.getTitle() + " (copy)";
        List<String> existingTitles = templateRepository
                .findTitlesByPrefix(baseTitle);

        String newTitle = baseTitle;
        if (existingTitles.contains(baseTitle)) {
            int counter = 2;
            while (existingTitles.contains(
                    baseTitle + " " + counter)) {
                counter++;
            }
            newTitle = baseTitle + " " + counter;
        }

        Template duplicate = Template.builder()
                .title(newTitle)
                .description(original.getDescription())
                .thumbnail(original.getThumbnail())
                .font(original.getFont())
                .primaryColor(original.getPrimaryColor())
                .secondaryColor(original.getSecondaryColor())
                .premium(original.getPremium())
                .status(TemplateStatus.INACTIVE)
                .tags(new ArrayList<>(original.getTags()))
                .build();

        Template saved = templateRepository.saveAndFlush(duplicate);
        log.info("Template duplicated: {} → {}",
                original.getTitle(), saved.getTitle());
        return TemplateResponse.from(saved);
    }

    // ─── Shared (Admin + Public) ─────────────────────────────────────

    @Transactional(readOnly = true)
    public TemplateResponse getById(UUID id) {
        return TemplateResponse.from(findTemplateById(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<TemplateResponse> getAll(
            String search,
            TemplateStatus status,
            Boolean premium,
            String tag,
            int page,
            int limit) {

        Specification<Template> spec = Specification.where(null);

        if (status != null) {
            spec = spec.and(TemplateSpecification.hasStatus(status));
        }
        if (premium != null) {
            spec = spec.and(TemplateSpecification.isPremium(premium));
        }
        if (tag != null && !tag.isBlank()) {
            spec = spec.and(TemplateSpecification.hasTag(tag));
        }
        if (search != null && !search.isBlank()) {
            spec = spec.and(
                    TemplateSpecification
                            .titleOrDescriptionContains(search));
        }

        Pageable pageable = PageRequest.of(
                page - 1,
                limit,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<Template> result = templateRepository.findAll(
                spec, pageable);

        List<TemplateResponse> data = result.getContent()
                .stream()
                .map(TemplateResponse::from)
                .toList();

        return new PageResponse<>(
                data,
                result.getTotalElements(),
                page,
                limit
        );
    }

    @Transactional
    public TemplateResponse uploadThumbnail(UUID id, MultipartFile file) {
        Template template = findTemplateById(id);

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Thumbnail file is required");
        }
        if (file.getSize() > 3 * 1024 * 1024) {
            throw new BadRequestException("File size exceeds 3MB limit");
        }
        if (!isImageType(file.getContentType())) {
            throw new BadRequestException(
                    "Only image files are allowed (JPG, PNG, WebP)");
        }

        // Delete old thumbnail from Cloudinary if exists
        if (template.getThumbnailPublicId() != null) {
            storageService.delete(template.getThumbnailPublicId());
        }

        // Upload new thumbnail
        StorageResult result = storageService.upload(
                file, StorageFolder.THUMBNAILS);

        template.setThumbnail(result.url());
        template.setThumbnailPublicId(result.publicId());

        Template updated = templateRepository.saveAndFlush(template);
        log.info("Thumbnail uploaded for template: {}", updated.getTitle());
        return TemplateResponse.from(updated);
    }

    private boolean isImageType(String mimeType) {
        return mimeType != null && (
                mimeType.equals("image/jpeg") ||
                        mimeType.equals("image/png") ||
                        mimeType.equals("image/webp") ||
                        mimeType.equals("image/gif")
        );
    }

    public String getTemplateSlug(UUID templateId) {
        return templateRepository.findById(templateId)
                .map(Template::getSlug)
                .orElse(null);
    }

    // ─── Private Helpers ─────────────────────────────────────────────

    private Template findTemplateById(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Template", "id", id));
    }

    public String[] getTemplateColors(UUID templateId) {
        if (templateId == null) return new String[]{"#111827", "#8b5cf6"};
        return templateRepository.findById(templateId)
                .map(t -> new String[]{t.getPrimaryColor(), t.getSecondaryColor()})
                .orElse(new String[]{"#111827", "#8b5cf6"});
    }

    public boolean isTemplatePremium(UUID templateId) {
        return templateRepository.findById(templateId)
                .map(t -> t.getPremium() != null && t.getPremium())
                .orElse(false);
    }
}