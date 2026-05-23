package com.cyopo.core.event.listener;

import com.cyopo.core.event.WeeklyDigestEvent;
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
public class WeeklyDigestEmailListener {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Async
    @EventListener
    public void onWeeklyDigest(WeeklyDigestEvent event) {
        try {
            Context context = new Context();
            context.setVariable("name",             event.getName());
            context.setVariable("viewsThisWeek",    event.getViewsThisWeek());
            context.setVariable("messagesThisWeek", event.getMessagesThisWeek());
            context.setVariable("portfolioCount",   event.getPortfolioCount());
            context.setVariable("dashboardUrl",     frontendUrl + "/dashboard");
            context.setVariable("analyticsUrl",     frontendUrl + "/analytics");
            context.setVariable("messagesUrl",      frontendUrl + "/messages");
            context.setVariable("settingsUrl",      frontendUrl + "/settings");

            String html = templateEngine.process(
                    "email/weekly-digest", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, "UTF-8");

            helper.setTo(event.getEmail());
            helper.setSubject("Your weekly cyopo digest 📊");
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Weekly digest sent to: {}", event.getEmail());

        } catch (Exception ex) {
            log.error("Failed to send weekly digest to {}: {}",
                    event.getEmail(), ex.getMessage());
        }
    }
}