package com.cyopo.common.exception;

import lombok.Getter;

/**
 * Thrown when a user tries to use a feature not available on their plan.
 * Contains enough info for frontend to show the right upgrade prompt.
 */
@Getter
public class FeatureGatedException extends RuntimeException {

    // Machine-readable code — frontend uses this to show specific upgrade UI
    // e.g. PORTFOLIO_LIMIT | PROJECT_LIMIT | CUSTOM_DOMAIN | RESUME_UPLOAD
    private final String featureCode;

    // User's current plan — frontend uses this to show "upgrade from X"
    private final String currentPlan;

    public FeatureGatedException(String message,
                                 String featureCode,
                                 String currentPlan) {
        super(message);
        this.featureCode = featureCode;
        this.currentPlan = currentPlan;
    }
}