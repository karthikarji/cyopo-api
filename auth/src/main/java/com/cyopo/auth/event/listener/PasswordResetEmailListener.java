package com.cyopo.auth.event.listener;

import com.cyopo.auth.event.PasswordResetRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
public class PasswordResetEmailListener {

    private final JavaMailSender  mailSender;
    private final TemplateEngine  templateEngine;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Async
    @EventListener
    public void onPasswordResetRequested(
            PasswordResetRequestedEvent event) {
        try {
            String resetLink = frontendUrl
                    + "/reset-password?token="
                    + event.getToken();

            Context context = new Context();
            context.setVariable("name",      event.getName());
            context.setVariable("resetLink", resetLink);

            String html = templateEngine.process(
                    "email/password-reset", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, "UTF-8");

            helper.setTo(event.getEmail());
            helper.setSubject("Reset your cyopo password");
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Password reset email sent to: {}",
                    event.getEmail());

        } catch (Exception ex) {
            log.error("Failed to send password reset email to {}: {}",
                    event.getEmail(), ex.getMessage());
        }
    }
}