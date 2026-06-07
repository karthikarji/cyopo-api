package com.cyopo.billing.repository;

import com.cyopo.billing.model.PlanChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlanChangeLogRepository extends JpaRepository<PlanChangeLog, UUID> {
    List<PlanChangeLog> findByUserIdOrderByChangedAtDesc(UUID userId);
}