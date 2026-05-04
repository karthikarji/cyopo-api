package com.cyopo.auth.event.listener;

import com.cyopo.auth.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class WelcomeEmailListener {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    // TODO: RabbitMQ - replace @Async with publishing to
    // "user.registered" exchange when scaling to multiple instances.
    // Current @Async works correctly on single instance only.
    @Async
    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        try {
            Context context = new Context();
            context.setVariable("name", event.getName());

            String html = templateEngine.process(
                    "email/welcome", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, "UTF-8");

            helper.setTo(event.getEmail());
            helper.setSubject("Welcome to cyopo!");
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Welcome email sent to: {}", event.getEmail());

        } catch (Exception ex) {
            log.error("Failed to send welcome email to {}: {}",
                    event.getEmail(), ex.getMessage());
        }
    }
}