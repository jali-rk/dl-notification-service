package dopaminelite.notifications.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.MessageRejectedException;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SesEmailServiceTest {

    @Test
    @DisplayName("sendEmail builds request and calls SES client")
    void sendEmail_buildsRequest() {
        SesClient mockClient = mock(SesClient.class);
        when(mockClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("mid-123").build());

        SesEmailService svc = new SesEmailService("no-reply@example.com", "us-east-1", mockClient);
        svc.sendEmail("user@example.com", "Subject", "Body");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(mockClient, times(1)).sendEmail(captor.capture());
        SendEmailRequest req = captor.getValue();

        assertEquals("no-reply@example.com", req.source());
        assertEquals("Subject", req.message().subject().data());
        assertEquals("Body", req.message().body().text().data());
        assertEquals(true, req.destination().toAddresses().contains("user@example.com"));
    }

    @Test
    @DisplayName("sendEmail with special characters encodes correctly")
    void sendEmail_withSpecialCharacters_encodesCorrectly() {
        SesClient mockClient = mock(SesClient.class);
        when(mockClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("mid-456").build());

        SesEmailService svc = new SesEmailService("no-reply@example.com", "us-east-1", mockClient);
        svc.sendEmail("user@example.com", "Test 🎉 Subject", "Body with émojis 😊 and ñ");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(mockClient).sendEmail(captor.capture());

        SendEmailRequest req = captor.getValue();
        assertEquals("Test 🎉 Subject", req.message().subject().data());
        assertEquals("Body with émojis 😊 and ñ", req.message().body().text().data());
    }

    @Test
    @DisplayName("sendEmailBatch with multiple recipients sends successfully")
    void sendEmailBatch_withMultipleRecipients_sendsSuccessfully() {
        SesClient mockClient = mock(SesClient.class);
        when(mockClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("mid-batch").build());

        SesEmailService svc = new SesEmailService("no-reply@example.com", "us-east-1", mockClient);
        List<String> recipients = Arrays.asList("user1@example.com", "user2@example.com", "user3@example.com");
        svc.sendEmailBatch(recipients, "Batch Subject", "Batch Body");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(mockClient).sendEmail(captor.capture());

        SendEmailRequest req = captor.getValue();
        assertEquals(3, req.destination().toAddresses().size());
        assertTrue(req.destination().toAddresses().containsAll(recipients));
    }

    @Test
    @DisplayName("sendEmailBatch with single recipient sends successfully")
    void sendEmailBatch_withSingleRecipient_sendsSuccessfully() {
        SesClient mockClient = mock(SesClient.class);
        when(mockClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("mid-single").build());

        SesEmailService svc = new SesEmailService("no-reply@example.com", "us-east-1", mockClient);
        svc.sendEmailBatch(Collections.singletonList("user@example.com"), "Subject", "Body");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(mockClient).sendEmail(captor.capture());

        SendEmailRequest req = captor.getValue();
        assertEquals(1, req.destination().toAddresses().size());
        assertEquals("user@example.com", req.destination().toAddresses().get(0));
    }

    @Test
    @DisplayName("sendEmail with SesException throws and logs error")
    void sendEmail_withSesException_throwsAndLogsError() {
        SesClient mockClient = mock(SesClient.class);
        when(mockClient.sendEmail(any(SendEmailRequest.class)))
                .thenThrow(SesException.builder().message("Rate limit exceeded").build());

        SesEmailService svc = new SesEmailService("no-reply@example.com", "us-east-1", mockClient);

        assertThrows(SesException.class, () ->
                svc.sendEmail("user@example.com", "Subject", "Body")
        );

        verify(mockClient).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    @DisplayName("sendEmail with MessageRejectedException throws exception")
    void sendEmail_withMessageRejectedException_throwsException() {
        SesClient mockClient = mock(SesClient.class);
        when(mockClient.sendEmail(any(SendEmailRequest.class)))
                .thenThrow(MessageRejectedException.builder().message("Invalid recipient").build());

        SesEmailService svc = new SesEmailService("no-reply@example.com", "us-east-1", mockClient);

        assertThrows(MessageRejectedException.class, () ->
                svc.sendEmail("invalid@example.com", "Subject", "Body")
        );
    }

    @Test
    @DisplayName("sendEmail with null recipient throws NullPointerException")
    void sendEmail_withNullRecipient_throwsNullPointerException() {
        SesClient mockClient = mock(SesClient.class);
        SesEmailService svc = new SesEmailService("no-reply@example.com", "us-east-1", mockClient);

        assertThrows(NullPointerException.class, () ->
                svc.sendEmail(null, "Subject", "Body")
        );
    }

    @Test
    @DisplayName("sendEmailBatch with empty list throws exception")
    void sendEmailBatch_withEmptyList_throwsException() {
        SesClient mockClient = mock(SesClient.class);
        when(mockClient.sendEmail(any(SendEmailRequest.class)))
                .thenThrow(SesException.builder().message("Empty recipient list").build());

        SesEmailService svc = new SesEmailService("no-reply@example.com", "us-east-1", mockClient);

        assertThrows(SesException.class, () ->
                svc.sendEmailBatch(Collections.emptyList(), "Subject", "Body")
        );
    }

    @Test
    @DisplayName("constructor with test client uses provided client")
    void constructor_withTestClient_usesProvidedClient() {
        SesClient mockClient = mock(SesClient.class);
        when(mockClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("mid-test").build());

        SesEmailService svc = new SesEmailService("no-reply@example.com", "us-east-1", mockClient);
        svc.sendEmail("user@example.com", "Subject", "Body");

        verify(mockClient).sendEmail(any(SendEmailRequest.class));
        // Verify no additional client was created
        verifyNoMoreInteractions(mockClient);
    }

    @Test
    @DisplayName("sendEmail with empty body sends successfully")
    void sendEmail_withEmptyBody_sendsSuccessfully() {
        SesClient mockClient = mock(SesClient.class);
        when(mockClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("mid-empty").build());

        SesEmailService svc = new SesEmailService("no-reply@example.com", "us-east-1", mockClient);
        svc.sendEmail("user@example.com", "Subject", "");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(mockClient).sendEmail(captor.capture());

        assertEquals("", captor.getValue().message().body().text().data());
    }

    @Test
    @DisplayName("sendEmail with large content sends successfully")
    void sendEmail_withLargeContent_sendsSuccessfully() {
        SesClient mockClient = mock(SesClient.class);
        when(mockClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("mid-large").build());

        String largeBody = "X".repeat(10000);
        String longSubject = "Subject ".repeat(50);

        SesEmailService svc = new SesEmailService("no-reply@example.com", "us-east-1", mockClient);
        svc.sendEmail("user@example.com", longSubject, largeBody);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(mockClient).sendEmail(captor.capture());

        assertEquals(longSubject, captor.getValue().message().subject().data());
        assertEquals(largeBody, captor.getValue().message().body().text().data());
    }
}
