package com.cyopo.core.controller;

import com.cyopo.common.response.ApiResponse;
import com.cyopo.core.dto.request.AiExperienceRequest;
import com.cyopo.core.dto.request.AiProfileRequest;
import com.cyopo.core.dto.request.AiProjectsRequest;
import com.cyopo.core.service.AiGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiGenerationService aiGenerationService;

    @PostMapping("/profile")
    public ResponseEntity<ApiResponse<Object>> generateProfile(
            @RequestBody AiProfileRequest request) {

        Object data = aiGenerationService.generateProfile(request);
        return ResponseEntity.ok(
                ApiResponse.success("Profile generated", data));
    }

    @PostMapping("/experience")
    public ResponseEntity<ApiResponse<Object>> generateExperience(
            @RequestBody AiExperienceRequest request) {

        Object data = aiGenerationService.generateExperience(request);
        return ResponseEntity.ok(
                ApiResponse.success("Experience generated", data));
    }

    @PostMapping("/projects")
    public ResponseEntity<ApiResponse<Object>> generateProjects(
            @RequestBody AiProjectsRequest request) {

        Object data = aiGenerationService.generateProjects(request);
        return ResponseEntity.ok(
                ApiResponse.success("Projects generated", data));
    }
}