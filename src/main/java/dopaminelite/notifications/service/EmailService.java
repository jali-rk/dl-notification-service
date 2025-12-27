package dopaminelite.notifications.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Email delivery via SMTP using Spring's JavaMailSender.
 * Configured to work with AWS SES SMTP endpoint.
 * Supports both plain text and HTML-formatted emails.
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

    /**
     * Send a rich text HTML email to a single recipient.
     * Supports HTML formatting and dynamic content.
     */
    public void sendHtmlEmail(String recipientEmail, String subject, String bodyHtml) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(senderEmail);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(bodyHtml, true); // true indicates HTML content
            
            mailSender.send(message);
            log.info("SMTP HTML email sent successfully to: {}", recipientEmail);
        } catch (MessagingException e) {
            log.error("SMTP HTML send failed to: {}", recipientEmail, e);
            throw new RuntimeException("Failed to send HTML email", e);
        }
    }

    /**
     * Send a rich text HTML email to multiple recipients.
     * Supports HTML formatting and dynamic content.
     */
    public void sendHtmlEmailBatch(List<String> recipientEmails, String subject, String bodyHtml) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(senderEmail);
            helper.setTo(recipientEmails.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(bodyHtml, true); // true indicates HTML content
            
            mailSender.send(message);
            log.info("SMTP HTML batch email sent successfully to: {}", recipientEmails);
        } catch (MessagingException e) {
            log.error("SMTP HTML batch send failed to: {}", recipientEmails, e);
            throw new RuntimeException("Failed to send HTML batch email", e);
        }
    }
}
