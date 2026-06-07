package com.cyopo.billing.service;

import com.cyopo.auth.model.User;
import com.cyopo.billing.model.Plan;
import com.cyopo.billing.repository.PlanRepository;
import com.cyopo.common.exception.FeatureGatedException;
import com.cyopo.common.exception.ResourceNotFoundException;
import com.cyopo.common.util.AppLogContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Enforces plan limits across the application.
 * Called by PortfolioService, ResumeService etc. before performing actions.
 * Throws FeatureGatedException (402) when user's plan does not allow the action.
 */
@Service
@RequiredArgsConstructor
public class FeatureGateService {

    private static final String CLASS = "FeatureGateService";

    private final PlanRepository planRepository;

    // ─── Portfolio Limits ─────────────────────────────────────────

    /**
     * Checks if user can create another portfolio.
     * Called before portfolio creation in PortfolioService.
     *
     * @throws FeatureGatedException if portfolio limit reached
     */
    public void checkPortfolioLimit(User user, int currentPortfolioCount) {
        Plan plan = getPlan(user);

        AppLogContext.debug(CLASS, "checkPortfolioLimit",
                "Checking portfolio limit",
                "userId", user.getId(),
                "current", currentPortfolioCount,
                "max", plan.getMaxPortfolios());

        if (currentPortfolioCount >= plan.getMaxPortfolios()) {
            AppLogContext.warn(CLASS, "checkPortfolioLimit",
                    "Portfolio limit reached",
                    "userId", user.getId(),
                    "plan", plan.getName(),
                    "current", currentPortfolioCount,
                    "max", plan.getMaxPortfolios());
            throw new FeatureGatedException(
                    String.format(
                            "Your %s plan allows up to %d portfolio(s). " +
                                    "Upgrade to create more.",
                            plan.getDisplayName(),
                            plan.getMaxPortfolios()),
                    "PORTFOLIO_LIMIT",
                    plan.getName());
        }
    }

    /**
     * Checks if user can add another project to a portfolio.
     * Called before project creation in PortfolioService.
     *
     * @throws FeatureGatedException if project limit reached
     */
    public void checkProjectLimit(User user, int currentProjectCount) {
        Plan plan = getPlan(user);

        // Integer.MAX_VALUE = unlimited — skip check
        if (plan.getMaxProjectsPerPortfolio() == Integer.MAX_VALUE) return;

        AppLogContext.debug(CLASS, "checkProjectLimit",
                "Checking project limit",
                "userId", user.getId(),
                "current", currentProjectCount,
                "max", plan.getMaxProjectsPerPortfolio());

        if (currentProjectCount >= plan.getMaxProjectsPerPortfolio()) {
            AppLogContext.warn(CLASS, "checkProjectLimit",
                    "Project limit reached",
                    "userId", user.getId(),
                    "plan", plan.getName(),
                    "current", currentProjectCount,
                    "max", plan.getMaxProjectsPerPortfolio());
            throw new FeatureGatedException(
                    String.format(
                            "Your %s plan allows up to %d project(s) per portfolio. " +
                                    "Upgrade to add more.",
                            plan.getDisplayName(),
                            plan.getMaxProjectsPerPortfolio()),
                    "PROJECT_LIMIT",
                    plan.getName());
        }
    }

    // ─── Feature Flags ────────────────────────────────────────────

    /**
     * Checks if user can use a custom domain.
     * PREMIUM and PRO only.
     *
     * @throws FeatureGatedException if user is on FREE plan
     */
    public void checkCustomDomain(User user) {
        Plan plan = getPlan(user);

        AppLogContext.debug(CLASS, "checkCustomDomain",
                "Checking custom domain access",
                "userId", user.getId(),
                "plan", plan.getName());

        if (!plan.isAllowCustomDomain()) {
            AppLogContext.warn(CLASS, "checkCustomDomain",
                    "Custom domain blocked — plan does not allow it",
                    "userId", user.getId(),
                    "plan", plan.getName());
            throw new FeatureGatedException(
                    "Custom domain is a Premium feature. " +
                            "Upgrade to connect your own domain.",
                    "CUSTOM_DOMAIN",
                    plan.getName());
        }
    }

    /**
     * Checks if user can use a premium template.
     * Free templates are always allowed regardless of plan.
     *
     * @throws FeatureGatedException if template is premium and user is on FREE
     */
    public void checkPremiumTemplate(User user, boolean templateIsPremium) {
        if (!templateIsPremium) return; // free template — always allowed

        Plan plan = getPlan(user);

        AppLogContext.debug(CLASS, "checkPremiumTemplate",
                "Checking premium template access",
                "userId", user.getId(),
                "plan", plan.getName());

        if (!plan.isAllowPremiumTemplates()) {
            AppLogContext.warn(CLASS, "checkPremiumTemplate",
                    "Premium template blocked — plan does not allow it",
                    "userId", user.getId(),
                    "plan", plan.getName());
            throw new FeatureGatedException(
                    "This template is available on Premium and Pro plans. " +
                            "Upgrade to use it.",
                    "PREMIUM_TEMPLATE",
                    plan.getName());
        }
    }

    // ─── Boolean Checks (for UI hints) ────────────────────────────

    /**
     * Returns true if user can create another portfolio.
     * Non-throwing version — use for UI gate checks.
     */
    public boolean canCreatePortfolio(User user, int currentCount) {
        try {
            checkPortfolioLimit(user, currentCount);
            return true;
        } catch (FeatureGatedException e) {
            return false;
        }
    }

    /**
     * Returns true if user can use a custom domain.
     * Non-throwing version — use for UI gate checks.
     */
    public boolean canUseCustomDomain(User user) {
        return getPlan(user).isAllowCustomDomain();
    }

    // ─── Private Helper ───────────────────────────────────────────

    /**
     * Fetches the full Plan entity from billing.plans using the
     * user's plan enum (FREE/PREMIUM/PRO) from auth.users.
     *
     * @throws ResourceNotFoundException if plan not configured in DB
     */
    private Plan getPlan(User user) {
        String planName = user.getPlan().name();
        return planRepository.findByName(planName)
                .orElseThrow(() -> {
                    AppLogContext.error(CLASS, "getPlan",
                            "Plan not found in billing.plans — misconfiguration",
                            "planName", planName,
                            "userId", user.getId());
                    return new ResourceNotFoundException(
                            "Plan", "name", planName);
                });
    }

    public boolean canUsePremiumTemplates(User user) {
        return getPlan(user).isAllowPremiumTemplates();
    }

    // Expose plan entity for controllers that need limit values
    public Plan getPlanForUser(User user) {
        return getPlan(user);
    }
}