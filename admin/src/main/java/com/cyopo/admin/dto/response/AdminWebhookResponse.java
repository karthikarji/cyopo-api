package com.cyopo.admin.dto.response;

/**
 * Webhook event data returned by admin billing API.
 * WebhookEvent has no lazy associations — mapped for consistency.
 */
public record AdminWebhookResponse(
        String id,
        String gateway,
        String eventId,
        String eventType,
        boolean processed,
        String errorMessage,
        int retryCount,
        String createdAt
) {
}