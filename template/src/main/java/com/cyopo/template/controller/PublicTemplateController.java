package com.cyopo.template.controller;

import com.cyopo.common.response.ApiResponse;
import com.cyopo.common.response.PageResponse;
import com.cyopo.template.dto.response.TemplateResponse;
import com.cyopo.template.model.TemplateStatus;
import com.cyopo.template.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/templates")
@RequiredArgsConstructor
public class PublicTemplateController {

    private final TemplateService templateService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TemplateResponse>>> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean premium,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {

        // Public endpoint always filters to ACTIVE templates only
        PageResponse<TemplateResponse> response =
                templateService.getAll(
                        search,
                        TemplateStatus.ACTIVE,
                        premium,
                        tag,
                        page,
                        limit
                );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TemplateResponse>> getById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(templateService.getById(id)));
    }
}