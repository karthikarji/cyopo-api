package com.cyopo.billing.service;

import com.cyopo.billing.model.Invoice;
import com.cyopo.common.storage.StorageFolder;
import com.cyopo.common.storage.StorageResult;
import com.cyopo.common.storage.StorageService;
import com.cyopo.common.util.AppLogContext;
import com.cyopo.common.util.ByteArrayMultipartFile;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Generates invoice PDFs and uploads them to Cloudinary.
 * Called by BillingService after invoice is created.
 */
@Service
@RequiredArgsConstructor
public class InvoicePdfService {

    private static final String CLASS = "InvoicePdfService";
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy")
                    .withZone(ZoneId.of("Asia/Kolkata"));

    private final StorageService storageService;

    @Value("${app.billing.seller-gstin:}")
    private String sellerGstin;

    /**
     * Generates a PDF for the given invoice and uploads to Cloudinary.
     * Returns the public URL, or null if generation fails (non-critical).
     */
    public String generateAndUpload(Invoice invoice) {
        try {
            byte[] pdfBytes = generatePdf(invoice);

            // Use custom wrapper instead of MockMultipartFile
            MultipartFile file = new ByteArrayMultipartFile(
                    pdfBytes,
                    invoice.getInvoiceNumber() + ".pdf",
                    "application/pdf"
            );

            StorageResult result = storageService.uploadBytes(
                    pdfBytes,
                    invoice.getInvoiceNumber() + ".pdf",
                    "application/pdf",
                    StorageFolder.INVOICES
            );

            AppLogContext.info(CLASS, "generateAndUpload",
                    "Invoice PDF generated and uploaded",
                    "invoiceNumber", invoice.getInvoiceNumber(),
                    "url", result.url());

            return result.url();

        } catch (Exception e) {
            AppLogContext.error(CLASS, "generateAndUpload",
                    "Failed to generate invoice PDF — invoice saved without PDF",
                    e, "invoiceNumber", invoice.getInvoiceNumber());
            return null;
        }
    }

    private byte[] generatePdf(Invoice invoice) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document doc = new Document(pdfDoc);

        // ── Header ────────────────────────────────────────────────
        doc.add(new Paragraph("cyopo")
                .setFontSize(24)
                .setBold()
                .setFontColor(ColorConstants.DARK_GRAY));

        doc.add(new Paragraph("TAX INVOICE")
                .setFontSize(14)
                .setBold()
                .setTextAlignment(TextAlignment.RIGHT));

        doc.add(new Paragraph(" "));

        // ── Invoice details ────────────────────────────────────────
        Table detailsTable = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100));

        detailsTable.addCell(noBorder(new Cell().add(
                new Paragraph("Invoice Number: " + invoice.getInvoiceNumber()))));
        detailsTable.addCell(noBorder(new Cell().add(
                new Paragraph("Date: " + DATE_FMT.format(invoice.getIssuedAt()))
                        .setTextAlignment(TextAlignment.RIGHT))));

        if (invoice.getPeriodStart() != null && invoice.getPeriodEnd() != null) {
            detailsTable.addCell(noBorder(new Cell().add(
                    new Paragraph("Period: " + DATE_FMT.format(invoice.getPeriodStart())
                            + " – " + DATE_FMT.format(invoice.getPeriodEnd())))));
            detailsTable.addCell(noBorder(new Cell()));
        }

        doc.add(detailsTable);
        doc.add(new Paragraph(" "));

        // ── Billing info ───────────────────────────────────────────
        doc.add(new Paragraph("Bill To:").setBold());
        doc.add(new Paragraph(invoice.getBillingName()));
        doc.add(new Paragraph(invoice.getBillingEmail()));
        if (invoice.getGstin() != null) {
            doc.add(new Paragraph("GSTIN: " + invoice.getGstin()));
        }
        doc.add(new Paragraph(" "));

        if (!sellerGstin.isBlank()) {
            doc.add(new Paragraph("Seller GSTIN: " + sellerGstin)
                    .setTextAlignment(TextAlignment.RIGHT));
        }

        // ── Amount table ───────────────────────────────────────────
        Table amountTable = new Table(UnitValue.createPercentArray(new float[]{70, 30}))
                .setWidth(UnitValue.createPercentValue(100));

        // Header row
        amountTable.addHeaderCell(headerCell("Description"));
        amountTable.addHeaderCell(headerCell("Amount"));

        // Plan row
        amountTable.addCell("Subscription");
        amountTable.addCell(formatAmount(invoice.getSubtotal() + invoice.getDiscount(),
                invoice.getCurrency()));

        // Discount
        if (invoice.getDiscount() > 0) {
            amountTable.addCell("Discount");
            amountTable.addCell("- " + formatAmount(invoice.getDiscount(),
                    invoice.getCurrency()));
        }

        // GST
        if (invoice.getGstAmount() > 0) {
            amountTable.addCell("GST (" + invoice.getGstRate().stripTrailingZeros() + "%)");
            amountTable.addCell(formatAmount(invoice.getGstAmount(),
                    invoice.getCurrency()));
        }

        // Total
        amountTable.addCell(new Cell().add(
                new Paragraph("Total").setBold()));
        amountTable.addCell(new Cell().add(
                new Paragraph(formatAmount(invoice.getTotal(), invoice.getCurrency()))
                        .setBold()));

        doc.add(amountTable);
        doc.add(new Paragraph(" "));

        // ── Footer ────────────────────────────────────────────────
        doc.add(new Paragraph("Thank you for choosing cyopo!")
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY)
                .setFontSize(10));

        doc.close();
        return baos.toByteArray();
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private Cell headerCell(String text) {
        return new Cell()
                .add(new Paragraph(text).setBold())
                .setBackgroundColor(ColorConstants.LIGHT_GRAY);
    }

    private Cell noBorder(Cell cell) {
        return cell.setBorder(null);
    }

    private String formatAmount(long amountInPaise, String currency) {
        String symbol = switch (currency) {
            case "INR" -> "₹";
            case "USD" -> "$";
            case "GBP" -> "£";
            default -> currency + " ";
        };
        BigDecimal amount = BigDecimal.valueOf(amountInPaise)
                .divide(BigDecimal.valueOf(100));
        return symbol + String.format("%.2f", amount);
    }
}