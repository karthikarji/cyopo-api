package com.cyopo.template.dto.response;

import com.cyopo.template.model.Template;
import com.cyopo.template.model.TemplateStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class TemplateResponse {

    private UUID id;
    private String title;
    private String description;
    private String thumbnail;
    private String font;
    private String primaryColor;
    private String secondaryColor;
    private Boolean premium;
    private TemplateStatus status;
    private List<String> tags;
    private Instant createdAt;
    private Instant updatedAt;

    public static TemplateResponse from(Template template) {
        return TemplateResponse.builder()
                .id(template.getId())
                .title(template.getTitle())
                .description(template.getDescription())
                .thumbnail(template.getThumbnail())
                .font(template.getFont())
                .primaryColor(template.getPrimaryColor())
                .secondaryColor(template.getSecondaryColor())
                .premium(template.getPremium())
                .status(template.getStatus())
                .tags(template.getTags())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}