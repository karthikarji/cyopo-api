package com.cyopo.core.controller;

import com.cyopo.common.response.ApiResponse;
import com.cyopo.core.dto.response.AnalyticsResponse;
import com.cyopo.core.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    public ResponseEntity<ApiResponse<AnalyticsResponse>> getAnalytics(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) List<UUID> portfolioIds,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant endDate,
            @RequestParam(defaultValue = "day") String interval) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        analyticsService.getAnalytics(
                                userId,
                                portfolioIds,
                                startDate,
                                endDate,
                                interval)));
    }
}