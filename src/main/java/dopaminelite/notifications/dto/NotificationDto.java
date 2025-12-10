package dopaminelite.notifications.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dopaminelite.notifications.entity.DeliveryStatus;
import dopaminelite.notifications.entity.NotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * API DTO representing a notification record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationDto {
    private UUID id;
    private UUID userId;
    private NotificationChannel channel;
    private String title;
    private String body;
    private boolean isRead;
    private Instant createdAt;
    private Instant readAt;
    private DeliveryStatus deliveryStatus;
    private String templateKey;
    private Map<String, Object> metadata;
}
