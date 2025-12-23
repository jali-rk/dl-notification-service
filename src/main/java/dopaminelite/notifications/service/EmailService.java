package dopaminelite.notifications.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Email delivery via SMTP using Spring's JavaMailSender.
 * Configured to work with AWS SES SMTP endpoint.
 */
@Slf4j
@Service
public class EmailService {

    private final String senderEmail;
    private final JavaMailSender mailSender;

    /**
     * Primary constructor for Spring dependency injection.
     */
    public EmailService(@Value("${notifications.email.sender}") String senderEmail, JavaMailSender mailSender) {
        this.senderEmail = senderEmail;
        this.mailSender = mailSender;
    }

    /**
     * Send a simple text email to a single recipient.
     */
    public void sendEmail(String recipientEmail, String subject, String bodyText) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(senderEmail);
            message.setTo(recipientEmail);
            message.setSubject(subject);
            message.setText(bodyText);

            mailSender.send(message);
            log.info("SMTP email sent successfully to: {}", recipientEmail);
        } catch (Exception e) {
            log.error("SMTP send failed to: {}", recipientEmail, e);
            throw e;
        }
    }

    /**
     * Send a simple text email to multiple recipients.
     */
    public void sendEmailBatch(List<String> recipientEmails, String subject, String bodyText) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(senderEmail);
            message.setTo(recipientEmails.toArray(new String[0]));
            message.setSubject(subject);
            message.setText(bodyText);

            mailSender.send(message);
            log.info("SMTP batch email sent successfully to: {}", recipientEmails);
        } catch (Exception e) {
            log.error("SMTP batch send failed to: {}", recipientEmails, e);
            throw e;
        }
    }
}
