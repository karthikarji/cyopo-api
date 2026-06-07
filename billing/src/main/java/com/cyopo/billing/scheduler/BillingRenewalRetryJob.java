package com.cyopo.billing.scheduler;

import com.cyopo.billing.model.Subscription;
import com.cyopo.billing.repository.SubscriptionRepository;
import com.cyopo.common.util.AppLogContext;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Retries payment for PAST_DUE subscriptions within their grace period.
 * <p>
 * Flow:
 * → Finds all PAST_DUE subscriptions where grace_period_end > now
 * → If retry count < MAX_RETRIES: increments count and marks last retry
 * → If retry count >= MAX_RETRIES: skips — BillingExpiryJob handles downgrade
 * <p>
 * NOTE: Actual Razorpay recurring payment retry requires Razorpay Subscription API.
 * Currently increments retry count only — full implementation pending.
 * <p>
 * Runs daily at 9am via app.scheduler.billing-renewal-retry-cron.
 * Try-catch per subscription — one failure must not stop others.
 */
@Component
@RequiredArgsConstructor
public class BillingRenewalRetryJob {

    private static final String CLASS = "BillingRenewalRetryJob";
    private static final int MAX_RETRIES = 3;

    private final SubscriptionRepository subscriptionRepository;

    @Scheduled(cron = "${app.scheduler.billing-renewal-retry-cron:0 0 9 * * *}")
    @Transactional
    public void retryFailedRenewals() {
        Instant now = Instant.now();

        AppLogContext.info(CLASS, "retryFailedRenewals",
                "Starting renewal retry job");

        List<Subscription> pastDue = subscriptionRepository
                .findPastDueWithinGracePeriod(now);

        if (pastDue.isEmpty()) {
            AppLogContext.debug(CLASS, "retryFailedRenewals",
                    "No PAST_DUE subscriptions within grace period");
            return;
        }

        AppLogContext.info(CLASS, "retryFailedRenewals",
                "Found PAST_DUE subscriptions to retry",
                "count", pastDue.size());

        int retried = 0;
        int maxed = 0;
        int failed = 0;

        for (Subscription sub : pastDue) {
            // Try-catch per subscription — one failure must not stop others
            try {
                if (sub.getRetryCount() >= MAX_RETRIES) {
                    // Max retries exhausted — BillingExpiryJob will downgrade
                    AppLogContext.info(CLASS, "retryFailedRenewals",
                            "Max retries reached — expiry job will downgrade",
                            "subscriptionId", sub.getId(),
                            "retryCount", sub.getRetryCount());
                    maxed++;
                    continue;
                }

                // TODO: Call Razorpay Subscription API to retry payment
                // Requires Razorpay recurring billing setup
                // For now — track retry count so grace period logic works
                sub.setRetryCount(sub.getRetryCount() + 1);
                sub.setLastRetryAt(now);
                subscriptionRepository.save(sub);

                AppLogContext.info(CLASS, "retryFailedRenewals",
                        "Retry attempt recorded",
                        "subscriptionId", sub.getId(),
                        "userId", sub.getUser().getId(),
                        "retryCount", sub.getRetryCount(),
                        "maxRetries", MAX_RETRIES,
                        "gracePeriodEnd", sub.getGracePeriodEnd());

                retried++;

            } catch (Exception e) {
                // Log and continue — do not rethrow
                AppLogContext.error(CLASS, "retryFailedRenewals",
                        "Failed to process renewal retry — investigate manually",
                        e,
                        "subscriptionId", sub.getId(),
                        "userId", sub.getUser().getId());
                failed++;
            }
        }

        AppLogContext.info(CLASS, "retryFailedRenewals",
                "Renewal retry job completed",
                "total", pastDue.size(),
                "retried", retried,
                "maxRetriesReached", maxed,
                "failed", failed);
    }
}