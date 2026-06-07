package com.cyopo.billing.scheduler;

import com.cyopo.billing.repository.PaymentRepository;
import com.cyopo.common.util.AppLogContext;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Masks IP addresses in billing.payments older than 30 days.
 * <p>
 * GDPR compliance — IP addresses are personal data and must not be
 * retained beyond the necessary retention period.
 * Masked value: "***.***.***.***"
 * ip_masked_at is set to record when masking occurred.
 * <p>
 * Runs daily at 2am via app.scheduler.billing-ip-masking-cron.
 * Bulk update — no per-item try-catch needed (@Transactional rolls back on failure).
 */
@Component
@RequiredArgsConstructor
public class IpMaskingJob {

    private static final String CLASS = "IpMaskingJob";
    private static final String MASKED_IP = "***.***.***.***";

    // 30 days — GDPR retention period for IP addresses
    private static final int RETENTION_DAYS = 30;

    private final PaymentRepository paymentRepository;

    @Scheduled(cron = "${app.scheduler.billing-ip-masking-cron:0 0 2 * * *}")
    @Transactional
    public void maskOldIpAddresses() {
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);

        AppLogContext.info(CLASS, "maskOldIpAddresses",
                "Starting IP masking job",
                "retentionDays", RETENTION_DAYS,
                "cutoff", cutoff);

        var payments = paymentRepository.findPaymentsForIpMasking(cutoff);

        if (payments.isEmpty()) {
            AppLogContext.debug(CLASS, "maskOldIpAddresses",
                    "No IP addresses to mask");
            return;
        }

        // No per-item try-catch — bulk update
        // @Transactional rolls back entire batch on failure
        // Job retries successfully on next daily run
        Instant now = Instant.now();
        payments.forEach(payment -> {
            payment.setIpAddress(MASKED_IP);
            payment.setIpMaskedAt(now);
        });

        paymentRepository.saveAll(payments);

        AppLogContext.info(CLASS, "maskOldIpAddresses",
                "IP addresses masked successfully",
                "count", payments.size());
    }
}