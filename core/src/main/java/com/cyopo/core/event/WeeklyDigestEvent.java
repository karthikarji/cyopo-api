package com.cyopo.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class WeeklyDigestEvent extends ApplicationEvent {

    private final String email;
    private final String name;
    private final long   viewsThisWeek;
    private final long   messagesThisWeek;
    private final int    portfolioCount;

    public WeeklyDigestEvent(
            Object source,
            String email,
            String name,
            long   viewsThisWeek,
            long   messagesThisWeek,
            int    portfolioCount) {
        super(source);
        this.email            = email;
        this.name             = name;
        this.viewsThisWeek    = viewsThisWeek;
        this.messagesThisWeek = messagesThisWeek;
        this.portfolioCount   = portfolioCount;
    }
}