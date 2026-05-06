package com.cyopo.template.repository;

import com.cyopo.template.model.Template;
import com.cyopo.template.model.TemplateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TemplateRepository extends JpaRepository<Template, UUID>,
        JpaSpecificationExecutor<Template> {

    boolean existsByTitle(String title);

    boolean existsByTitleAndIdNot(String title, UUID id);

    Optional<Template> findByTitle(String title);

    // Used for slug-style duplicate title generation
    @Query("SELECT t.title FROM Template t WHERE t.title LIKE :prefix%")
    List<String> findTitlesByPrefix(String prefix);

    // Count active templates — used by public listing
    long countByStatus(TemplateStatus status);
}