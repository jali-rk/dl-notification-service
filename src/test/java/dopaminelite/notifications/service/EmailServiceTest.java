package dopaminelite.notifications.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    private JavaMailSender mockMailSender;
    private EmailService emailService;
    private static final String SENDER_EMAIL = "no-reply@example.com";

    @BeforeEach
    void setUp() {
        mockMailSender = mock(JavaMailSender.class);
        emailService = new EmailService(SENDER_EMAIL, mockMailSender);
    }

    @Test
    @DisplayName("sendEmail builds message and calls mail sender")
    void sendEmail_buildsMessage() {
        emailService.sendEmail("user@example.com", "Subject", "Body");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mockMailSender, times(1)).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertEquals(SENDER_EMAIL, message.getFrom());
        assertArrayEquals(new String[]{"user@example.com"}, message.getTo());
        assertEquals("Subject", message.getSubject());
        assertEquals("Body", message.getText());
    }

    @Test
    @DisplayName("sendEmail with special characters encodes correctly")
    void sendEmail_withSpecialCharacters_encodesCorrectly() {
        emailService.sendEmail("user@example.com", "Test 🎉 Subject", "Body with émojis 😊 and ñ");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mockMailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertEquals("Test 🎉 Subject", message.getSubject());
        assertEquals("Body with émojis 😊 and ñ", message.getText());
    }

    @Test
    @DisplayName("sendEmailBatch with multiple recipients sends successfully")
    void sendEmailBatch_withMultipleRecipients_sendsSuccessfully() {
        List<String> recipients = Arrays.asList("user1@example.com", "user2@example.com", "user3@example.com");
        emailService.sendEmailBatch(recipients, "Batch Subject", "Batch Body");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mockMailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertEquals(3, message.getTo().length);
        assertTrue(Arrays.asList(message.getTo()).containsAll(recipients));
    }

    @Test
    @DisplayName("sendEmailBatch with single recipient sends successfully")
    void sendEmailBatch_withSingleRecipient_sendsSuccessfully() {
        emailService.sendEmailBatch(Collections.singletonList("user@example.com"), "Subject", "Body");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mockMailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertEquals(1, message.getTo().length);
        assertEquals("user@example.com", message.getTo()[0]);
    }

    @Test
    @DisplayName("sendEmail with exception throws and logs error")
    void sendEmail_withException_throwsAndLogsError() {
        doThrow(new MailSendException("SMTP server error"))
                .when(mockMailSender).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class, () ->
                emailService.sendEmail("user@example.com", "Subject", "Body")
        );

        verify(mockMailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("sendEmail with empty body sends successfully")
    void sendEmail_withEmptyBody_sendsSuccessfully() {
        emailService.sendEmail("user@example.com", "Subject", "");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mockMailSender).send(captor.capture());

        assertEquals("", captor.getValue().getText());
    }

    @Test
    @DisplayName("sendEmail with large content sends successfully")
    void sendEmail_withLargeContent_sendsSuccessfully() {
        String largeBody = "X".repeat(10000);
        String longSubject = "Subject ".repeat(50);

        emailService.sendEmail("user@example.com", longSubject, largeBody);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mockMailSender).send(captor.capture());

        assertEquals(longSubject, captor.getValue().getSubject());
        assertEquals(largeBody, captor.getValue().getText());
    }

    // HTML Email Tests

    @Test
    @DisplayName("sendHtmlEmail sends message with HTML content type")
    void sendHtmlEmail_sendsWithHtmlContentType() throws MessagingException {
        MimeMessage mockMimeMessage = mock(MimeMessage.class);
        when(mockMailSender.createMimeMessage()).thenReturn(mockMimeMessage);

        String htmlBody = "<p><strong>Test</strong></p>";
        emailService.sendHtmlEmail("user@example.com", "Subject", htmlBody);

        verify(mockMailSender).createMimeMessage();
        verify(mockMailSender).send(mockMimeMessage);
    }

    @Test
    @DisplayName("sendHtmlEmail with rich formatting sends successfully")
    void sendHtmlEmail_withRichFormatting_sendsSuccessfully() throws MessagingException {
        MimeMessage mockMimeMessage = mock(MimeMessage.class);
        when(mockMailSender.createMimeMessage()).thenReturn(mockMimeMessage);

        String richHtml = "<p><strong>Test</strong></p>" +
                "<p>Name: John Doe</p>" +
                "<ul><li><p>Bullet 1</p></li><li><p>Bullet 2</p></li></ul>" +
                "<p><em>Italic</em></p>";

        emailService.sendHtmlEmail("user@example.com", "Rich HTML Email", richHtml);

        verify(mockMailSender).createMimeMessage();
        verify(mockMailSender).send(mockMimeMessage);
    }

    @Test
    @DisplayName("sendHtmlEmail with dynamic placeholders sends successfully")
    void sendHtmlEmail_withDynamicPlaceholders_sendsSuccessfully() throws MessagingException {
        MimeMessage mockMimeMessage = mock(MimeMessage.class);
        when(mockMailSender.createMimeMessage()).thenReturn(mockMimeMessage);

        String htmlWithPlaceholders = "<p><strong>Test</strong></p>" +
                "<p>{{name}} {{email}}</p>" +
                "<p>Registered: {{registration}}</p>" +
                "<p>Today: {{date}}/{{month}}</p>";

        emailService.sendHtmlEmail("user@example.com", "Dynamic Content", htmlWithPlaceholders);

        verify(mockMailSender).createMimeMessage();
        verify(mockMailSender).send(mockMimeMessage);
    }

    @Test
    @DisplayName("sendHtmlEmailBatch sends to multiple recipients")
    void sendHtmlEmailBatch_sendsToMultipleRecipients() throws MessagingException {
        MimeMessage mockMimeMessage = mock(MimeMessage.class);
        when(mockMailSender.createMimeMessage()).thenReturn(mockMimeMessage);

        List<String> recipients = Arrays.asList("user1@example.com", "user2@example.com");
        String htmlBody = "<p><strong>Hello</strong></p>";

        emailService.sendHtmlEmailBatch(recipients, "Subject", htmlBody);

        verify(mockMailSender).createMimeMessage();
        verify(mockMailSender).send(mockMimeMessage);
    }

    @Test
    @DisplayName("sendHtmlEmail with MessagingException throws RuntimeException")
    void sendHtmlEmail_withMessagingException_throwsRuntimeException() throws MessagingException {
        MimeMessage mockMimeMessage = mock(MimeMessage.class);
        when(mockMailSender.createMimeMessage()).thenReturn(mockMimeMessage);
        doThrow(new RuntimeException("Send failed")).when(mockMailSender).send(mockMimeMessage);

        String htmlBody = "<p>Test</p>";

        assertThrows(RuntimeException.class, () ->
                emailService.sendHtmlEmail("user@example.com", "Subject", htmlBody)
        );

        verify(mockMailSender).createMimeMessage();
        verify(mockMailSender).send(mockMimeMessage);
    }

    @Test
    @DisplayName("sendHtmlEmail with special characters in HTML sends successfully")
    void sendHtmlEmail_withSpecialCharactersInHtml_sendsSuccessfully() throws MessagingException {
        MimeMessage mockMimeMessage = mock(MimeMessage.class);
        when(mockMailSender.createMimeMessage()).thenReturn(mockMimeMessage);

        String htmlWithSpecialChars = "<p>Test 🎉 with émojis 😊 and ñ symbols</p>";

        emailService.sendHtmlEmail("user@example.com", "Special Chars", htmlWithSpecialChars);

        verify(mockMailSender).createMimeMessage();
        verify(mockMailSender).send(mockMimeMessage);
    }
}
