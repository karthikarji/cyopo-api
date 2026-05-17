package com.cyopo.core.controller;

import com.cyopo.common.exception.BadRequestException;
import com.cyopo.common.exception.ResourceNotFoundException;
import com.cyopo.common.response.ApiResponse;
import com.cyopo.core.model.PortfolioResume;
import com.cyopo.core.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping("/api/v1/user/portfolios/{portfolioId}/resume")
    public ResponseEntity<ApiResponse<Void>> uploadResume(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID portfolioId,
            @RequestParam("file") MultipartFile file) {
        try {
            resumeService.uploadResume(userId, portfolioId, file);
            return ResponseEntity.ok(
                    ApiResponse.success("Resume uploaded successfully"));
        } catch (BadRequestException | ResourceNotFoundException ex) {
            log.warn("Resume upload failed for portfolio {}: {}",
                    portfolioId, ex.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            log.error("Unexpected error uploading resume for portfolio {}: {}",
                    portfolioId, ex.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(
                            "An unexpected error occurred. Please try again."));
        }
    }

    @DeleteMapping("/api/v1/user/portfolios/{portfolioId}/resume")
    public ResponseEntity<ApiResponse<Void>> deleteResume(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID portfolioId) {
        try {
            resumeService.deleteResume(userId, portfolioId);
            return ResponseEntity.ok(
                    ApiResponse.success("Resume deleted successfully"));
        } catch (ResourceNotFoundException ex) {
            log.warn("Resume delete failed for portfolio {}: {}",
                    portfolioId, ex.getMessage());
            return ResponseEntity.status(404)
                    .body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            log.error("Unexpected error deleting resume for portfolio {}: {}",
                    portfolioId, ex.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(
                            "An unexpected error occurred. Please try again."));
        }
    }

    @GetMapping("/api/v1/public/portfolios/{portfolioId}/resume")
    public ResponseEntity<?> downloadResume(
            @PathVariable UUID portfolioId) {
        try {
            PortfolioResume resume = resumeService.getResume(portfolioId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + resume.getFileName() + "\"")
                    .contentType(MediaType.parseMediaType(resume.getMimeType()))
                    .contentLength(resume.getFileData().length)
                    .body(resume.getFileData());
        } catch (ResourceNotFoundException ex) {
            log.warn("Resume download failed for portfolio {}: {}",
                    portfolioId, ex.getMessage());
            return ResponseEntity.status(404)
                    .body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            log.error("Unexpected error downloading resume for portfolio {}: {}",
                    portfolioId, ex.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(
                            "An unexpected error occurred. Please try again."));
        }
    }
}