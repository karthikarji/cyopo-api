package com.cyopo.core.event.listener;

import com.cyopo.core.event.PortfolioContactReceivedEvent;
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
public class PortfolioContactEmailListener {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    // TODO: RabbitMQ - replace @Async with publishing to
    // "portfolio.contact" exchange when scaling to multiple instances.
    // Risk: on single instance @Async email failure is silent.
    // RabbitMQ gives retry + dead letter queue for failed sends.
    @Async
    @EventListener
    public void onContactReceived(
            PortfolioContactReceivedEvent event) {
        try {
            Context context = new Context();
            context.setVariable("senderName", event.getSenderName());
            context.setVariable("senderEmail", event.getSenderEmail());
            context.setVariable("subject", event.getSubject());
            context.setVariable("message", event.getMessage());

            String html = templateEngine.process(
                    "email/contact", context);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage, true, "UTF-8");

            helper.setTo(event.getPortfolioOwnerEmail());
            helper.setSubject("📬 New Contact: " + event.getSubject());
            helper.setText(html, true);

            mailSender.send(mimeMessage);
            log.info("Contact email sent to: {}",
                    event.getPortfolioOwnerEmail());

        } catch (Exception ex) {
            log.error("Failed to send contact email: {}",
                    ex.getMessage());
        }
    }
}