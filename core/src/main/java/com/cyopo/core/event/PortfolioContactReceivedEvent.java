package com.cyopo.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PortfolioContactReceivedEvent extends ApplicationEvent {

    private final String portfolioOwnerEmail;
    private final String senderName;
    private final String senderEmail;
    private final String subject;
    private final String message;

    public PortfolioContactReceivedEvent(
            Object source,
            String portfolioOwnerEmail,
            String senderName,
            String senderEmail,
            String subject,
            String message) {
        super(source);
        this.portfolioOwnerEmail = portfolioOwnerEmail;
        this.senderName = senderName;
        this.senderEmail = senderEmail;
        this.subject = subject;
        this.message = message;
    }
}