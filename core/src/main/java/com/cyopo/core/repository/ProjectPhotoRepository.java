package com.cyopo.core.repository;

import com.cyopo.core.model.ProjectPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectPhotoRepository
        extends JpaRepository<ProjectPhoto, UUID> {

    List<ProjectPhoto> findByProjectIdOrderBySortOrderAsc(UUID projectId);

    long countByProjectId(UUID projectId);

    @Modifying
    @Query("UPDATE ProjectPhoto p SET p.isThumbnail = false WHERE p.project.id = :projectId")
    void clearThumbnail(UUID projectId);
}