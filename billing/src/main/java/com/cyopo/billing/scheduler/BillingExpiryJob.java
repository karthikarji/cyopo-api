package com.cyopo.billing.scheduler;

import com.cyopo.auth.model.Plan;
import com.cyopo.auth.repository.UserRepository;
import com.cyopo.billing.model.PlanChangeLog;
import com.cyopo.billing.model.Subscription;
import com.cyopo.billing.repository.PlanChangeLogRepository;
import com.cyopo.billing.repository.SubscriptionRepository;
import com.cyopo.common.util.AppLogContext;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Downgrades users whose subscription period has ended.
 * <p>
 * Handles two cases:
 * 1. cancel_at_period_end=true AND current_period_end < now
 * → user cancelled, paid period is over, downgrade to FREE
 * 2. PAST_DUE AND grace_period_end < now
 * → renewal failed, grace period exhausted, downgrade to FREE
 * <p>
 * Runs daily at midnight via app.scheduler.billing-expiry-cron.
 * Each subscription is processed independently — one failure does not
 * stop others from being processed.
 */
@Component
@RequiredArgsConstructor
public class BillingExpiryJob {

    private static final String CLASS = "BillingExpiryJob";

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PlanChangeLogRepository planChangeLogRepository;

    @Scheduled(cron = "${app.scheduler.billing-expiry-cron:0 0 0 * * *}")
    @Transactional
    public void expireSubscriptions() {
        Instant now = Instant.now();
        AppLogContext.info(CLASS, "expireSubscriptions",
                "Starting subscription expiry job", "at", now);

        // ── Case 1: Cancelled subscriptions past period end ────────
        List<Subscription> cancelled = subscriptionRepository
                .findExpiredSubscriptions(now);

        AppLogContext.info(CLASS, "expireSubscriptions",
                "Found cancelled subscriptions to expire",
                "count", cancelled.size());

        cancelled.forEach(sub -> processDowngrade(sub, "EXPIRY"));

        // ── Case 2: PAST_DUE past grace period ────────────────────
        List<Subscription> graceExpired = subscriptionRepository
                .findPastDueGraceExpired(now);

        AppLogContext.info(CLASS, "expireSubscriptions",
                "Found PAST_DUE subscriptions past grace period",
                "count", graceExpired.size());

        graceExpired.forEach(sub -> processDowngrade(sub, "EXPIRY"));

        int total = cancelled.size() + graceExpired.size();
        if (total > 0) {
            AppLogContext.info(CLASS, "expireSubscriptions",
                    "Expiry job completed",
                    "totalDowngraded", total);
        } else {
            AppLogContext.debug(CLASS, "expireSubscriptions",
                    "No subscriptions to expire");
        }
    }

    /**
     * Downgrades a single subscription to FREE.
     * Try-catch per subscription — one failure must not stop others.
     */
    private void processDowngrade(Subscription sub, String reason) {
        // Try-catch required in scheduled jobs:
        // one bad subscription must not prevent others from being processed
        try {
            String previousPlan = sub.getPlan().getName();
            var user = sub.getUser();

            // Mark subscription EXPIRED
            sub.setStatus("EXPIRED");
            subscriptionRepository.save(sub);

            // Downgrade user plan in auth.users
            user.setPlan(Plan.FREE);
            userRepository.save(user);

            // Immutable audit log entry
            planChangeLogRepository.save(PlanChangeLog.builder()
                    .user(user)
                    .fromPlan(previousPlan)
                    .toPlan("FREE")
                    .reason(reason)
                    .subscription(sub)
                    .build());

            AppLogContext.info(CLASS, "processDowngrade",
                    "User downgraded to FREE",
                    "userId", user.getId(),
                    "email", user.getEmail(),
                    "fromPlan", previousPlan,
                    "reason", reason);

        } catch (Exception e) {
            // Log and continue — do not rethrow
            // Failed subscription will be retried on next job run
            AppLogContext.error(CLASS, "processDowngrade",
                    "Failed to downgrade subscription — will retry on next run",
                    e,
                    "subscriptionId", sub.getId(),
                    "userId", sub.getUser().getId());
        }
    }
}