package com.cyopo.auth.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PasswordResetRequestedEvent extends ApplicationEvent {

    private final String email;
    private final String name;
    private final String token;

    public PasswordResetRequestedEvent(Object source, String email,
                                       String name, String token) {
        super(source);
        this.email = email;
        this.name  = name;
        this.token = token;
    }
}