package com.cyopo.template.controller;

import com.cyopo.common.response.ApiResponse;
import com.cyopo.common.response.PageResponse;
import com.cyopo.template.dto.request.CreateTemplateRequest;
import com.cyopo.template.dto.request.UpdateTemplateRequest;
import com.cyopo.template.dto.response.TemplateResponse;
import com.cyopo.template.model.TemplateStatus;
import com.cyopo.template.service.TemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminTemplateController {

    private final TemplateService templateService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TemplateResponse>>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TemplateStatus status,
            @RequestParam(required = false) Boolean premium,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {

        PageResponse<TemplateResponse> response =
                templateService.getAll(
                        search, status, premium, tag, page, limit);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TemplateResponse>> getById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(templateService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TemplateResponse>> create(
            @Valid @RequestBody CreateTemplateRequest request) {
        TemplateResponse response = templateService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Template created successfully", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TemplateResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTemplateRequest request) {
        TemplateResponse response = templateService.update(id, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Template updated successfully", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id) {
        templateService.delete(id);
        return ResponseEntity.ok(
                ApiResponse.success("Template deleted successfully"));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ApiResponse<TemplateResponse>> duplicate(
            @PathVariable UUID id) {
        TemplateResponse response = templateService.duplicate(id);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Template duplicated successfully", response));
    }
}