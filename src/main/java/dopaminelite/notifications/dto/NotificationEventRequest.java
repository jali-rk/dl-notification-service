package dopaminelite.notifications.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import dopaminelite.notifications.entity.enums.NotificationEventType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generic domain event that triggers notifications.
 * Contains event type, target user(s), optional actor, channels, and payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationEventRequest {
    
    @NotNull(message = "Event type is required")
    private NotificationEventType eventType;
    
    @NotNull(message = "Primary user ID is required")
    private UUID primaryUserId;
    
    private UUID actorUserId;
    
    private List<NotificationChannel> channels;
    
    private Map<String, Object> payload;
}
