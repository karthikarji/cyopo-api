package com.cyopo.core.service;

import com.cyopo.common.exception.BadRequestException;
import com.cyopo.common.exception.ResourceNotFoundException;
import com.cyopo.common.storage.StorageFolder;
import com.cyopo.common.storage.StorageResult;
import com.cyopo.common.storage.StorageService;
import com.cyopo.common.util.AppLogContext;
import com.cyopo.core.model.Portfolio;
import com.cyopo.core.model.PortfolioResume;
import com.cyopo.core.repository.PortfolioRepository;
import com.cyopo.core.repository.PortfolioResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResumeService {

    private static final String CLASS = "ResumeService";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private static final String[] ALLOWED_TYPES = {
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    };

    private final PortfolioRepository portfolioRepository;
    private final PortfolioResumeRepository resumeRepository;
    private final StorageService storageService;

    /**
     * Uploads a resume to Cloudinary and links it to the portfolio.
     * Replaces any existing resume — only one resume per portfolio.
     * Validates file type (PDF, DOC, DOCX) and size (max 5MB).
     *
     * @throws ResourceNotFoundException if portfolio not found or not owned by user
     * @throws BadRequestException       if file is missing, too large, or wrong type
     */
    @Transactional
    public void uploadResume(String userId, UUID portfolioId,
                             MultipartFile file) {
        AppLogContext.info(CLASS, "uploadResume",
                "Resume upload requested",
                "portfolioId", portfolioId,
                "userId", userId);

        Portfolio portfolio = portfolioRepository
                .findByIdAndUserId(portfolioId, UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "id", portfolioId));

        // ── Validate file ──────────────────────────────────────────
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Resume file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException(
                    "File size exceeds 5MB limit. " +
                            "Actual size: " + (file.getSize() / 1024 / 1024) + "MB");
        }

        String contentType = file.getContentType();
        boolean validType = Arrays.asList(ALLOWED_TYPES).contains(contentType);
        if (!validType) {
            AppLogContext.warn(CLASS, "uploadResume",
                    "Invalid file type rejected",
                    "portfolioId", portfolioId,
                    "contentType", contentType);
            throw new BadRequestException(
                    "Only PDF and Word documents are allowed (PDF, DOC, DOCX)");
        }

        // ── Delete existing resume from Cloudinary ─────────────────
        // Fire-and-forget — if delete fails, upload still proceeds
        // Old Cloudinary asset may become orphaned but that is acceptable
        resumeRepository.findByPortfolioId(portfolioId)
                .ifPresent(existing -> {
                    storageService.delete(existing.getCloudPublicId());
                    resumeRepository.delete(existing);
                    resumeRepository.flush();
                    AppLogContext.debug(CLASS, "uploadResume",
                            "Existing resume deleted from Cloudinary",
                            "portfolioId", portfolioId,
                            "oldPublicId", existing.getCloudPublicId());
                });

        // ── Upload to Cloudinary ───────────────────────────────────
        StorageResult result = storageService.upload(file, StorageFolder.RESUMES);

        // ── Persist resume record ──────────────────────────────────
        resumeRepository.save(PortfolioResume.builder()
                .portfolioId(portfolioId)
                .fileName(file.getOriginalFilename())
                .fileSize((int) file.getSize())
                .mimeType(contentType)
                .fileUrl(result.url())
                .cloudPublicId(result.publicId())
                .build());

        // ── Update portfolio metadata ──────────────────────────────
        portfolio.setResumeFileName(file.getOriginalFilename());
        portfolio.setResumeFileSize((int) file.getSize());
        portfolioRepository.save(portfolio);

        AppLogContext.info(CLASS, "uploadResume",
                "Resume uploaded successfully",
                "portfolioId", portfolioId,
                "fileName", file.getOriginalFilename(),
                "fileSize", file.getSize(),
                "url", result.url());
    }

    /**
     * Deletes resume from Cloudinary and removes the record.
     *
     * @throws ResourceNotFoundException if portfolio or resume not found
     */
    @Transactional
    public void deleteResume(String userId, UUID portfolioId) {
        AppLogContext.info(CLASS, "deleteResume",
                "Resume deletion requested",
                "portfolioId", portfolioId,
                "userId", userId);

        Portfolio portfolio = portfolioRepository
                .findByIdAndUserId(portfolioId, UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "id", portfolioId));

        PortfolioResume resume = resumeRepository
                .findByPortfolioId(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Resume", "portfolioId", portfolioId));

        storageService.delete(resume.getCloudPublicId());
        resumeRepository.delete(resume);

        portfolio.setResumeFileName(null);
        portfolio.setResumeFileSize(null);
        portfolioRepository.save(portfolio);

        AppLogContext.info(CLASS, "deleteResume",
                "Resume deleted successfully",
                "portfolioId", portfolioId,
                "fileName", resume.getFileName());
    }

    /**
     * Returns the Cloudinary URL for a portfolio's resume.
     *
     * @throws ResourceNotFoundException if no resume exists for the portfolio
     */
    @Transactional(readOnly = true)
    public String getResumeUrl(UUID portfolioId) {
        AppLogContext.debug(CLASS, "getResumeUrl",
                "Fetching resume URL", "portfolioId", portfolioId);

        return resumeRepository.findByPortfolioId(portfolioId)
                .map(PortfolioResume::getFileUrl)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Resume", "portfolioId", portfolioId));
    }
}