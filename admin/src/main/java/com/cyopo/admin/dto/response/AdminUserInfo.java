package com.cyopo.admin.dto.response;

/**
 * Compact user info included in admin billing responses.
 * Avoids lazy loading issues by extracting only needed fields
 * inside the transaction boundary.
 */
public record AdminUserInfo(
        String id,
        String email,
        String name
) {
}