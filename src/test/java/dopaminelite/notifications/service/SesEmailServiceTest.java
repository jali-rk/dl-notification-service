package dopaminelite.notifications.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
