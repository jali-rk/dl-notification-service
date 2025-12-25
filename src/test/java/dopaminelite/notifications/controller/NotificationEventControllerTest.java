package dopaminelite.notifications.controller;

import dopaminelite.notifications.dto.NotificationEventRequest;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import dopaminelite.notifications.entity.enums.NotificationEventType;
import dopaminelite.notifications.exception.GlobalExceptionHandler;
import dopaminelite.notifications.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationEventControllerTest {
        private final NotificationService notificationService = Mockito.mock(NotificationService.class);
        private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NotificationEventController(notificationService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();

    @Test
        @DisplayName("POST /notification-events accepts event")
    void processNotificationEvent() throws Exception {
        UUID userId = UUID.randomUUID();
        
        NotificationEventRequest req = NotificationEventRequest.builder()
                .eventType(NotificationEventType.PAYMENT_STATUS_CHANGED)
                .primaryUserId(userId)
                .channels(List.of(NotificationChannel.EMAIL, NotificationChannel.IN_APP))
                .payload(Map.of("submissionId", "abc", "oldStatus", "PENDING", "newStatus", "APPROVED"))
                .build();

        Mockito.doNothing().when(notificationService).processNotificationEvent(Mockito.any());

        String json = "{" +
                "\"eventType\":\"PAYMENT_STATUS_CHANGED\"," +
                "\"primaryUserId\":\"" + userId + "\"," +
                "\"channels\":[\"EMAIL\",\"IN_APP\"]," +
                "\"payload\":{\"submissionId\":\"abc\",\"oldStatus\":\"PENDING\",\"newStatus\":\"APPROVED\"}" +
                "}";

        mockMvc.perform(post("/notification-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isAccepted());
    }
}
