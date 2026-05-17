package com.cyopo.core.service;

import com.cyopo.common.exception.BadRequestException;
import com.cyopo.common.exception.ResourceNotFoundException;
import com.cyopo.core.model.Portfolio;
import com.cyopo.core.model.PortfolioResume;
import com.cyopo.core.repository.PortfolioRepository;
import com.cyopo.core.repository.PortfolioResumeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private static final long     MAX_FILE_SIZE  = 5 * 1024 * 1024;
    private static final String[] ALLOWED_TYPES  = {
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    };

    private final PortfolioRepository portfolioRepository;
    private final PortfolioResumeRepository resumeRepository;

    @Transactional
    public void uploadResume(String userId, UUID portfolioId, MultipartFile file) {
        Portfolio portfolio = portfolioRepository
                .findByIdAndUserId(portfolioId, UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "id", portfolioId));

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Resume file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds 5MB limit");
        }

        String  contentType = file.getContentType();
        boolean validType   = false;
        for (String allowed : ALLOWED_TYPES) {
            if (allowed.equals(contentType)) { validType = true; break; }
        }
        if (!validType) {
            throw new BadRequestException(
                    "Only PDF and Word documents are allowed");
        }

        resumeRepository.deleteByPortfolioId(portfolioId);

        try {
            PortfolioResume resume = PortfolioResume.builder()
                    .portfolioId(portfolioId)
                    .fileName(file.getOriginalFilename())
                    .fileSize((int) file.getSize())
                    .mimeType(contentType)
                    .fileData(file.getBytes())
                    .build();
            resumeRepository.save(resume);
        } catch (IOException e) {
            log.error("Failed to read resume file for portfolio {}: {}",
                    portfolioId, e.getMessage());
            throw new BadRequestException(
                    "Failed to read uploaded file. Please try again.");
        }

        portfolio.setResumeFileName(file.getOriginalFilename());
        portfolio.setResumeFileSize((int) file.getSize());
        portfolioRepository.save(portfolio);

        log.info("Resume uploaded for portfolio: {}", portfolioId);
    }

    @Transactional
    public void deleteResume(String userId, UUID portfolioId) {
        Portfolio portfolio = portfolioRepository
                .findByIdAndUserId(portfolioId, UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Portfolio", "id", portfolioId));

        if (!resumeRepository.existsByPortfolioId(portfolioId)) {
            throw new ResourceNotFoundException(
                    "Resume", "portfolioId", portfolioId);
        }

        resumeRepository.deleteByPortfolioId(portfolioId);

        portfolio.setResumeFileName(null);
        portfolio.setResumeFileSize(null);
        portfolioRepository.save(portfolio);

        log.info("Resume deleted for portfolio: {}", portfolioId);
    }

    @Transactional(readOnly = true)
    public PortfolioResume getResume(UUID portfolioId) {
        return resumeRepository.findByPortfolioId(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Resume", "portfolioId", portfolioId));
    }
}