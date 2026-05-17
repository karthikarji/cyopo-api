package com.cyopo.core.repository;

import com.cyopo.core.model.Contact;
import com.cyopo.core.model.ContactStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ContactRepository extends JpaRepository<Contact, UUID> {

    boolean existsByPortfolioIdAndEmail(UUID portfolioId, String email);

    Page<Contact> findByPortfolioIdOrderByCreatedAtDesc(
            UUID portfolioId, Pageable pageable);

    long countByPortfolioId(UUID portfolioId);

    long countByPortfolioIdAndStatus(UUID portfolioId, ContactStatus status);

    @Modifying
    @Query("UPDATE Contact c SET c.status = 'READ' WHERE c.id = :id")
    void markAsRead(UUID id);
}