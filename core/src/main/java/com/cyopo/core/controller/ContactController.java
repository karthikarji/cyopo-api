package com.cyopo.core.controller;

import com.cyopo.common.response.ApiResponse;
import com.cyopo.common.response.PageResponse;
import com.cyopo.common.util.AppLogContext;
import com.cyopo.core.dto.response.ContactResponse;
import com.cyopo.core.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Handles contact message management for portfolio owners.
 * All authenticated endpoints verify portfolio ownership inside ContactService.
 * No try-catch — GlobalExceptionHandler handles all exceptions.
 */
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class ContactController {

    private static final String CLASS = "ContactController";

    private final ContactService contactService;

    // ─── Get Messages ─────────────────────────────────────────────────

    /**
     * GET /api/v1/user/portfolios/:portfolioId/messages
     * Returns paginated messages for a specific portfolio.
     * Verifies ownership inside ContactService.
     */
    @GetMapping("/portfolios/{portfolioId}/messages")
    public ResponseEntity<ApiResponse<PageResponse<ContactResponse>>> getMessages(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID portfolioId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {

        AppLogContext.info(CLASS, "getMessages",
                "Fetching portfolio messages",
                "portfolioId", portfolioId,
                "userId", userId,
                "page", page);

        return ResponseEntity.ok(ApiResponse.success(
                contactService.getPortfolioMessages(
                        userId, portfolioId, page, limit)));
    }

    // ─── Get Stats (single portfolio) ────────────────────────────────

    /**
     * GET /api/v1/user/portfolios/:portfolioId/messages/stats
     * Returns total + unread count for a specific portfolio.
     * Kept for individual portfolio lookups.
     */
    @GetMapping("/portfolios/{portfolioId}/messages/stats")
    public ResponseEntity<ApiResponse<ContactService.ContactStatsResponse>> getStats(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID portfolioId) {

        AppLogContext.debug(CLASS, "getStats",
                "Fetching portfolio stats",
                "portfolioId", portfolioId,
                "userId", userId);

        return ResponseEntity.ok(ApiResponse.success(
                contactService.getPortfolioContactStats(userId, portfolioId)));
    }

    // ─── Get All Portfolio Stats ──────────────────────────────────────

    /**
     * GET /api/v1/user/messages/stats-all
     * Returns message stats for ALL portfolios owned by the user.
     * Single call replaces N per-portfolio stats calls on the messages page.
     * Includes portfolioName and portfolioSlug so frontend needs no extra calls.
     */
    @GetMapping("/messages/stats-all")
    public ResponseEntity<ApiResponse<List<ContactService.PortfolioStatsResponse>>> getAllStats(
            @AuthenticationPrincipal String userId) {

        AppLogContext.info(CLASS, "getAllStats",
                "Fetching all portfolio stats",
                "userId", userId);

        return ResponseEntity.ok(ApiResponse.success(
                contactService.getAllPortfolioStats(userId)));
    }

    // ─── Mark As Read ─────────────────────────────────────────────────

    /**
     * PATCH /api/v1/user/messages/:messageId/read
     * Marks a contact message as READ.
     * Verifies ownership inside ContactService.
     */
    @PatchMapping("/messages/{messageId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID messageId) {

        AppLogContext.debug(CLASS, "markAsRead",
                "Mark as read request",
                "messageId", messageId,
                "userId", userId);

        contactService.markAsRead(userId, messageId);
        return ResponseEntity.ok(ApiResponse.success("Message marked as read"));
    }
}