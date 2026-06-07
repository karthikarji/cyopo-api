package com.cyopo.billing.repository;

import com.cyopo.billing.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Generate sequential invoice number using DB sequence
    @Query(value = "SELECT 'INV-' || EXTRACT(YEAR FROM now()) || '-' || LPAD(nextval('billing.invoice_seq')::text, 6, '0')", nativeQuery = true)
    String generateInvoiceNumber();

    Page<Invoice> findByStatus(String status, Pageable pageable);
}