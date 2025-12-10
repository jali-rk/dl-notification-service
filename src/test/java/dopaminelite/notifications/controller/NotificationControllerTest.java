package dopaminelite.notifications.controller;

import dopaminelite.notifications.dto.NotificationDto;
import dopaminelite.notifications.dto.NotificationListResponse;
import dopaminelite.notifications.dto.NotificationReadUpdateRequest;
import dopaminelite.notifications.entity.DeliveryStatus;
import dopaminelite.notifications.entity.NotificationChannel;
import dopaminelite.notifications.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationControllerTest {
        private final NotificationService notificationService = Mockito.mock(NotificationService.class);
        private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();

    @Test
    @DisplayName("GET /api/v1/notifications returns list for user")
    void listNotifications() throws Exception {
        UUID userId = UUID.randomUUID();
        NotificationDto dto = NotificationDto.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .channel(NotificationChannel.IN_APP)
                .title("Hello")
                .body("World")
                .isRead(false)
                .createdAt(Instant.parse("2025-11-30T10:00:00Z"))
                .deliveryStatus(DeliveryStatus.PENDING)
                .build();
        NotificationListResponse response = NotificationListResponse.builder()
                .items(List.of(dto))
                .total(1)
                .build();

        Mockito.when(notificationService.listNotifications(Mockito.eq(userId), Mockito.eq(false), Mockito.isNull(), Mockito.eq(20), Mockito.eq(0)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/notifications")
                        .param("userId", userId.toString())
                        .param("unreadOnly", "false")
                        .param("limit", "20")
                        .param("offset", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].userId").value(userId.toString()))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/notifications/{id} returns a notification")
    void getNotification() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        NotificationDto dto = NotificationDto.builder()
                .id(id)
                .userId(userId)
                .channel(NotificationChannel.IN_APP)
                .title("Hello")
                .body("World")
                .isRead(false)
                .createdAt(Instant.parse("2025-11-30T10:00:00Z"))
                .deliveryStatus(DeliveryStatus.SENT)
                .build();

        Mockito.when(notificationService.getNotification(id)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/notifications/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("PATCH /api/v1/notifications/{id}/read marks as read")
    void markAsRead() throws Exception {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        NotificationReadUpdateRequest req = NotificationReadUpdateRequest.builder().isRead(true).build();
        NotificationDto dto = NotificationDto.builder()
                .id(id)
                .userId(userId)
                .channel(NotificationChannel.IN_APP)
                .title("Hello")
                .body("World")
                .isRead(true)
                .readAt(Instant.parse("2025-11-30T11:00:00Z"))
                .createdAt(Instant.parse("2025-11-30T10:00:00Z"))
                .deliveryStatus(DeliveryStatus.SENT)
                .build();

        Mockito.when(notificationService.markAsRead(id, req)).thenReturn(dto);

        mockMvc.perform(patch("/api/v1/notifications/" + id + "/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isRead\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").exists());
    }
}
