package com.cyopo.billing.scheduler;

import com.cyopo.billing.gateway.PaymentGateway;
import com.cyopo.billing.gateway.dto.GatewayPaymentDetails;
import com.cyopo.billing.model.BillingOrder;
import com.cyopo.billing.model.Payment;
import com.cyopo.billing.repository.PaymentRepository;
import com.cyopo.billing.service.BillingService;
import com.cyopo.common.exception.GatewayException;
import com.cyopo.common.util.AppLogContext;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Reconciliation job — finds payments captured on Razorpay but not yet
 * activated in our DB.
 * <p>
 * Fixes the "user paid but plan not upgraded" scenario caused by:
 * → Webhook delivery failure or delay
 * → User closed browser before /verify was called
 * → Network timeout between Razorpay and our backend
 * <p>
 * Looks back 24 hours to cover any missed webhooks.
 * Runs every 6 hours via app.scheduler.billing-reconcile-cron.
 * <p>
 * Try-catch per payment — one failure must not stop reconciliation of others.
 */
@Component
@RequiredArgsConstructor
public class BillingReconcileJob {

    private static final String CLASS = "BillingReconcileJob";

    private final PaymentRepository paymentRepository;
    private final BillingService billingService;
    private final PaymentGateway paymentGateway;

    @Scheduled(cron = "${app.scheduler.billing-reconcile-cron:0 0 */6 * * *}")
    public void reconcile() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);

        AppLogContext.info(CLASS, "reconcile",
                "Starting reconciliation job",
                "lookbackHours", 24);

        List<Payment> unactivated = paymentRepository
                .findCapturedWithoutSubscription(since);

        if (unactivated.isEmpty()) {
            AppLogContext.debug(CLASS, "reconcile",
                    "No unactivated payments found — system is consistent");
            return;
        }

        // WARN — these should normally be zero
        // A non-zero count means webhooks were missed or /verify was not called
        AppLogContext.warn(CLASS, "reconcile",
                "Found unactivated payments — reconciling",
                "count", unactivated.size());

        int success = 0;
        int failed = 0;

        for (Payment payment : unactivated) {
            try {
                // ── Verify status with Razorpay ────────────────────
                // Try-catch required — external HTTP call to Razorpay
                GatewayPaymentDetails details;
                try {
                    details = paymentGateway
                            .fetchPayment(payment.getGatewayPaymentId());
                } catch (GatewayException e) {
                    AppLogContext.error(CLASS, "reconcile",
                            "Failed to fetch payment from gateway — skipping",
                            e,
                            "paymentId", payment.getId(),
                            "gatewayPaymentId", payment.getGatewayPaymentId());
                    failed++;
                    continue;
                }

                // Only activate if Razorpay confirms captured
                if (!"captured".equals(details.getStatus())) {
                    AppLogContext.warn(CLASS, "reconcile",
                            "Payment not captured on gateway — skipping",
                            "paymentId", payment.getId(),
                            "gatewayStatus", details.getStatus());
                    continue;
                }

                // ── Activate subscription ──────────────────────────
                // activateSubscription() is idempotent — safe to call
                BillingOrder order = payment.getOrder();
                billingService.activateSubscription(
                        order,
                        order.getUser(),
                        details,
                        payment.getGatewayPaymentId());

                AppLogContext.info(CLASS, "reconcile",
                        "Payment reconciled — subscription activated",
                        "paymentId", payment.getId(),
                        "userId", order.getUser().getId(),
                        "email", order.getUser().getEmail());

                success++;

            } catch (Exception e) {
                // Log and continue — one failure must not stop others
                AppLogContext.error(CLASS, "reconcile",
                        "Reconciliation failed for payment — investigate manually",
                        e,
                        "paymentId", payment.getId(),
                        "userId", payment.getUser().getId());
                failed++;
            }
        }

        AppLogContext.info(CLASS, "reconcile",
                "Reconciliation job completed",
                "total", unactivated.size(),
                "success", success,
                "failed", failed);
    }
}