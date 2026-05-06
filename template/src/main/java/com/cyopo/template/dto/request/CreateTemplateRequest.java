package com.cyopo.template.dto.request;

import com.cyopo.template.model.TemplateStatus;
import jakarta.validation.constraints.*;
import lombok.Getter;

import java.util.List;

@Getter
public class CreateTemplateRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title cannot exceed 100 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @NotBlank(message = "Thumbnail is required")
    @Pattern(
            regexp = "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$",
            message = "Thumbnail must be a valid URL"
    )
    private String thumbnail;

    @NotBlank(message = "Font is required")
    @Size(max = 100, message = "Font cannot exceed 100 characters")
    private String font;

    @NotBlank(message = "Primary color is required")
    @Pattern(
            regexp = "^#(?:[0-9a-fA-F]{3}){1,2}$",
            message = "Primary color must be a valid hex color"
    )
    private String primaryColor;

    @NotBlank(message = "Secondary color is required")
    @Pattern(
            regexp = "^#(?:[0-9a-fA-F]{3}){1,2}$",
            message = "Secondary color must be a valid hex color"
    )
    private String secondaryColor;

    @NotNull(message = "Premium flag is required")
    private Boolean premium;

    @NotNull(message = "Status is required")
    private TemplateStatus status;

    @NotEmpty(message = "At least one tag is required")
    @Size(max = 10, message = "Cannot have more than 10 tags")
    private List<String> tags;
}