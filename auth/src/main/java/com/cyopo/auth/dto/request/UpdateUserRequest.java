package com.cyopo.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UpdateUserRequest {

    @Email(message = "Invalid email format")
    private String email;

    @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
    private String name;

}