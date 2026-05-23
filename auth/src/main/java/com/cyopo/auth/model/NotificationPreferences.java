package com.cyopo.auth.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreferences {
    @Builder.Default
    private boolean emailOnMessage = true;
    @Builder.Default
    private boolean weeklyDigest   = true;
}