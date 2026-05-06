package com.cyopo.template.dto.request;

import com.cyopo.template.model.TemplateStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
public class UpdateTemplateRequest {

    @Size(max = 100, message = "Title cannot exceed 100 characters")
    private String title;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @Pattern(
            regexp = "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$",
            message = "Thumbnail must be a valid URL"
    )
    private String thumbnail;

    @Size(max = 100, message = "Font cannot exceed 100 characters")
    private String font;

    @Pattern(
            regexp = "^#(?:[0-9a-fA-F]{3}){1,2}$",
            message = "Primary color must be a valid hex color"
    )
    private String primaryColor;

    @Pattern(
            regexp = "^#(?:[0-9a-fA-F]{3}){1,2}$",
            message = "Secondary color must be a valid hex color"
    )
    private String secondaryColor;

    private Boolean premium;

    private TemplateStatus status;

    @Size(max = 10, message = "Cannot have more than 10 tags")
    private List<String> tags;
}