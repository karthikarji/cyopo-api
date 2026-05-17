package com.cyopo.core.controller;

import com.cyopo.common.response.ApiResponse;
import com.cyopo.common.response.PageResponse;
import com.cyopo.core.dto.response.ContactResponse;
import com.cyopo.core.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    @GetMapping("/portfolios/{portfolioId}/messages")
    public ResponseEntity<ApiResponse<PageResponse<ContactResponse>>> getMessages(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID portfolioId,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    contactService.getPortfolioMessages(
                            userId, portfolioId, page, limit)));
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/portfolios/{portfolioId}/messages/stats")
    public ResponseEntity<ApiResponse<ContactService.ContactStatsResponse>> getStats(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID portfolioId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    contactService.getPortfolioContactStats(
                            userId, portfolioId)));
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PatchMapping("/messages/{messageId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID messageId) {
        try {
            contactService.markAsRead(userId, messageId);
            return ResponseEntity.ok(
                    ApiResponse.success("Message marked as read"));
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }
}