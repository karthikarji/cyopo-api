package com.cyopo.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SlugValidationResponse {
    private boolean available;
    private String message;
}