package com.cyopo.auth.dto.response;

import com.cyopo.auth.model.*;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class UserResponse {

    private UUID id;
    private String name;
    private String email;
    private Role role;
    private Plan plan;
    private UserStatus status;
    private NotificationPreferences notificationPreferences;
    private Instant createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .plan(user.getPlan())
                .status(user.getStatus())
                .notificationPreferences(
                        user.getNotificationPreferences())
                .createdAt(user.getCreatedAt())
                .build();
    }
}