package com.cyopo.auth.dto.request;

import com.cyopo.auth.model.NotificationPreferences;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UpdateUserRequest {

    @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
    private String name;

    private NotificationPreferences notificationPreferences;

}