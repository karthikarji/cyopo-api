package com.cyopo.core.controller;

import com.cyopo.common.response.ApiResponse;
import com.cyopo.core.dto.response.ProjectPhotoResponse;
import com.cyopo.core.service.ProjectPhotoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/user/portfolios/{portfolioId}/projects/{projectId}/photos")
@RequiredArgsConstructor
public class ProjectPhotoController {

    private final ProjectPhotoService photoService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectPhotoResponse>>> getPhotos(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID portfolioId,
            @PathVariable UUID projectId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    photoService.getPhotos(userId, portfolioId, projectId)));
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PatchMapping("/{photoId}/thumbnail")
    public ResponseEntity<ApiResponse<Void>> setThumbnail(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID portfolioId,
            @PathVariable UUID projectId,
            @PathVariable UUID photoId) {
        try {
            photoService.setThumbnail(
                    userId, portfolioId, projectId, photoId);
            return ResponseEntity.ok(
                    ApiResponse.success("Thumbnail updated"));
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<List<ProjectPhotoResponse>>> uploadPhotos(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID portfolioId,
            @PathVariable UUID projectId,
            @RequestParam("files") List<MultipartFile> files) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Photos uploaded successfully",
                    photoService.uploadPhotos(
                            userId, portfolioId, projectId, files)));
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @DeleteMapping("/{photoId}")
    public ResponseEntity<ApiResponse<Void>> deletePhoto(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID portfolioId,
            @PathVariable UUID projectId,
            @PathVariable UUID photoId) {
        try {
            photoService.deletePhoto(
                    userId, portfolioId, projectId, photoId);
            return ResponseEntity.ok(
                    ApiResponse.success("Photo deleted"));
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }
}