package dopaminelite.notifications.controller;

import dopaminelite.notifications.exception.GlobalExceptionHandler;
import dopaminelite.notifications.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DirectSendControllerTest {
    private final NotificationService notificationService = Mockito.mock(NotificationService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DirectSendController(notificationService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("POST /notifications/send accepts direct send")
    void sendDirectNotifications() throws Exception {
        UUID broadcastId = UUID.randomUUID();
        Mockito.when(notificationService.sendDirectNotifications(Mockito.any(), Mockito.any()))
            .thenReturn(broadcastId);

        String json = "{" +
                "\"targetUserIds\":[\"c0a80101-0000-0000-0000-000000000001\"]," +
                "\"channels\":[\"IN_APP\",\"EMAIL\"]," +
                "\"title\":\"System Maintenance\"," +
                "\"body\":\"The system will be down...\"" +
                "}";

        mockMvc.perform(post("/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.broadcastId").exists());
    }
}
