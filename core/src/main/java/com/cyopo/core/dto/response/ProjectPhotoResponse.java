package com.cyopo.core.dto.response;

import com.cyopo.core.model.ProjectPhoto;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ProjectPhotoResponse {

    private UUID    id;
    private String  fileUrl;
    private String  fileName;
    private Integer fileSize;
    private boolean isThumbnail;
    private Integer sortOrder;
    private Instant uploadedAt;

    public static ProjectPhotoResponse from(ProjectPhoto photo) {
        return ProjectPhotoResponse.builder()
                .id(photo.getId())
                .fileUrl(photo.getFileUrl())
                .fileName(photo.getFileName())
                .fileSize(photo.getFileSize())
                .isThumbnail(Boolean.TRUE.equals(photo.getIsThumbnail()))
                .sortOrder(photo.getSortOrder())
                .uploadedAt(photo.getUploadedAt())
                .build();
    }
}