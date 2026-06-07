package com.cyopo.admin.dto.response;

/**
 * Compact plan info included in admin billing responses.
 */
public record AdminPlanInfo(
        String name,
        String displayName
) {
}