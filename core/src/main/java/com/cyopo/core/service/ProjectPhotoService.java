package com.cyopo.core.service;

import com.cyopo.common.exception.BadRequestException;
import com.cyopo.common.exception.ResourceNotFoundException;
import com.cyopo.common.storage.StorageFolder;
import com.cyopo.common.storage.StorageResult;
import com.cyopo.common.storage.StorageService;
import com.cyopo.core.dto.response.ProjectPhotoResponse;
import com.cyopo.core.model.Portfolio;
import com.cyopo.core.model.Project;
import com.cyopo.core.model.ProjectPhoto;
import com.cyopo.core.repository.PortfolioRepository;
import com.cyopo.core.repository.ProjectPhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectPhotoService {

    private static final long MAX_FILE_SIZE    = 3 * 1024 * 1024; // 3MB
    private static final int  MAX_PHOTOS       = 5;

    private final PortfolioRepository    portfolioRepository;
    private final ProjectPhotoRepository photoRepository;
    private final StorageService         storageService;

    // ─── Upload photo ─────────────────────────────────────────────────

    @Transactional
    public ProjectPhotoResponse uploadPhoto(
            String userId, UUID portfolioId,
            UUID projectId, MultipartFile file) {

        Project project = resolveProject(userId, portfolioId, projectId);

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds 3MB limit");
        }
        if (!isImageType(file.getContentType())) {
            throw new BadRequestException(
                    "Only image files are allowed (JPG, PNG, WebP, GIF)");
        }
        if (photoRepository.countByProjectId(projectId) >= MAX_PHOTOS) {
            throw new BadRequestException(
                    "Maximum " + MAX_PHOTOS + " photos allowed per project");
        }

        StorageResult result = storageService.upload(
                file, StorageFolder.THUMBNAILS);

        // First photo auto-set as thumbnail
        boolean isFirst = photoRepository.countByProjectId(projectId) == 0;

        ProjectPhoto photo = ProjectPhoto.builder()
                .project(project)
                .fileUrl(result.url())
                .publicId(result.publicId())
                .fileName(file.getOriginalFilename())
                .fileSize((int) file.getSize())
                .isThumbnail(isFirst)
                .sortOrder((int) photoRepository.countByProjectId(projectId))
                .build();

        ProjectPhoto saved = photoRepository.save(photo);

        // Update project thumbnail if first photo
        if (isFirst) {
            project.setThumbnail(result.url());
            project.setThumbnailPublicId(result.publicId());
        }

        log.info("Photo uploaded for project: {}", projectId);
        return ProjectPhotoResponse.from(saved);
    }

    // ─── Set thumbnail ────────────────────────────────────────────────

    @Transactional
    public void setThumbnail(
            String userId, UUID portfolioId,
            UUID projectId, UUID photoId) {

        Project project = resolveProject(userId, portfolioId, projectId);

        ProjectPhoto photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Photo", "id", photoId));

        if (!photo.getProject().getId().equals(projectId)) {
            throw new BadRequestException(
                    "Photo does not belong to this project");
        }

        // Clear existing thumbnail flag
        photoRepository.clearThumbnail(projectId);

        // Set new thumbnail
        photo.setIsThumbnail(true);
        photoRepository.save(photo);

        // Update denormalized thumbnail on project
        project.setThumbnail(photo.getFileUrl());
        project.setThumbnailPublicId(photo.getPublicId());

        log.info("Thumbnail set to photo {} for project: {}",
                photoId, projectId);
    }

    @Transactional
    public List<ProjectPhotoResponse> uploadPhotos(
            String userId, UUID portfolioId,
            UUID projectId, List<MultipartFile> files) {

        Project project = resolveProject(userId, portfolioId, projectId);

        if (files == null || files.isEmpty()) {
            throw new BadRequestException("At least one file is required");
        }

        long currentCount = photoRepository.countByProjectId(projectId);

        if (currentCount + files.size() > MAX_PHOTOS) {
            throw new BadRequestException(
                    "Cannot upload " + files.size() + " photos. " +
                            "Maximum " + MAX_PHOTOS + " photos allowed. " +
                            "Currently have " + currentCount + ".");
        }

        List<ProjectPhotoResponse> results = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file.getSize() > MAX_FILE_SIZE) {
                throw new BadRequestException(
                        file.getOriginalFilename() + " exceeds 3MB limit");
            }
            if (!isImageType(file.getContentType())) {
                throw new BadRequestException(
                        file.getOriginalFilename() + " is not a valid image");
            }

            StorageResult result = storageService.upload(
                    file, StorageFolder.THUMBNAILS);

            boolean isFirst = currentCount == 0 && results.isEmpty();

            ProjectPhoto photo = ProjectPhoto.builder()
                    .project(project)
                    .fileUrl(result.url())
                    .publicId(result.publicId())
                    .fileName(file.getOriginalFilename())
                    .fileSize((int) file.getSize())
                    .isThumbnail(isFirst)
                    .sortOrder((int) (currentCount + results.size()))
                    .build();

            ProjectPhoto saved = photoRepository.save(photo);

            if (isFirst) {
                project.setThumbnail(result.url());
                project.setThumbnailPublicId(result.publicId());
            }

            results.add(ProjectPhotoResponse.from(saved));
        }

        log.info("Uploaded {} photos for project: {}", files.size(), projectId);
        return results;
    }

    // ─── Delete photo ─────────────────────────────────────────────────

    @Transactional
    public void deletePhoto(
            String userId, UUID portfolioId,
            UUID projectId, UUID photoId) {

        Project project = resolveProject(userId, portfolioId, projectId);

        ProjectPhoto photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Photo", "id", photoId));

        if (!photo.getProject().getId().equals(projectId)) {
            throw new BadRequestException(
                    "Photo does not belong to this project");
        }

        boolean wasThumbnail = Boolean.TRUE.equals(photo.getIsThumbnail());

        // Delete from Cloudinary
        storageService.delete(photo.getPublicId());
        photoRepository.delete(photo);

        // If deleted photo was thumbnail — auto assign next photo
        if (wasThumbnail) {
            List<ProjectPhoto> remaining = photoRepository
                    .findByProjectIdOrderBySortOrderAsc(projectId);
            if (!remaining.isEmpty()) {
                ProjectPhoto next = remaining.get(0);
                next.setIsThumbnail(true);
                photoRepository.save(next);
                project.setThumbnail(next.getFileUrl());
                project.setThumbnailPublicId(next.getPublicId());
            } else {
                project.setThumbnail(null);
                project.setThumbnailPublicId(null);
            }
        }

        log.info("Photo {} deleted for project: {}", photoId, projectId);
    }

    // ─── Get photos ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProjectPhotoResponse> getPhotos(
            String userId, UUID portfolioId, UUID projectId) {

        resolveProject(userId, portfolioId, projectId);

        return photoRepository
                .findByProjectIdOrderBySortOrderAsc(projectId)
                .stream()
                .map(ProjectPhotoResponse::from)
                .toList();
    }

    // ─── Private helpers ──────────────────────────────────────────────

    private Project resolveProject(
            String userId, UUID portfolioId, UUID projectId) {

        Portfolio portfolio = portfolioRepository
                .findByIdAndUserId(portfolioId, UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "id", portfolioId));

        return portfolio.getProjects().stream()
                .filter(p -> p.getId().equals(projectId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project", "id", projectId));
    }

    private boolean isImageType(String mimeType) {
        return mimeType != null && (
                mimeType.equals("image/jpeg") ||
                        mimeType.equals("image/png")  ||
                        mimeType.equals("image/webp") ||
                        mimeType.equals("image/gif")
        );
    }
}