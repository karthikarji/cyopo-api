package com.cyopo.core.repository;

import com.cyopo.core.model.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ContactRepository extends JpaRepository<Contact, UUID> {

    boolean existsByPortfolioIdAndEmail(UUID portfolioId, String email);
}