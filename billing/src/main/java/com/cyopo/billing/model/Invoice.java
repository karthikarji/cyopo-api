package com.cyopo.billing.model;

import com.cyopo.auth.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoices", schema = "billing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private BillingOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    // INV-2026-000001
    @Column(name = "invoice_number", nullable = false, unique = true, length = 30)
    private String invoiceNumber;

    // DRAFT | ISSUED | PAID | VOID
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ISSUED";

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    // All amounts in smallest unit (paise for INR)
    @Column(nullable = false)
    @Builder.Default
    private long subtotal = 0L;

    @Column(nullable = false)
    @Builder.Default
    private long discount = 0L;

    @Column(name = "gst_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal gstRate = BigDecimal.ZERO;

    @Column(name = "gst_amount", nullable = false)
    @Builder.Default
    private long gstAmount = 0L;

    @Column(nullable = false)
    @Builder.Default
    private long total = 0L;

    // ─── Billing snapshot at invoice time ─────────────────────────
    // Stored as snapshot — address changes do not affect past invoices
    @Column(name = "billing_name", length = 255)
    private String billingName;

    @Column(name = "billing_email", length = 255)
    private String billingEmail;

    @Column(name = "billing_address", columnDefinition = "TEXT")
    private String billingAddress;

    // Buyer's GSTIN (optional — for B2B)
    @Column(length = 15)
    private String gstin;

    // Your company's GSTIN (from application.yml)
    @Column(name = "seller_gstin", length = 15)
    private String sellerGstin;

    // Period this invoice covers
    @Column(name = "period_start")
    private Instant periodStart;

    @Column(name = "period_end")
    private Instant periodEnd;

    // PDF stored in Cloudinary
    @Column(name = "pdf_url", columnDefinition = "TEXT")
    private String pdfUrl;

    @Column(name = "pdf_public_id", length = 255)
    private String pdfPublicId;

    @Column(name = "issued_at", nullable = false)
    @Builder.Default
    private Instant issuedAt = Instant.now();

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}