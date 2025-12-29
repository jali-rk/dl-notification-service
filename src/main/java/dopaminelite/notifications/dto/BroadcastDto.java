package dopaminelite.notifications.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for broadcast record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BroadcastDto {
    private UUID id;
    private UUID templateId;
    private String title;
    private String body;
    private List<NotificationChannel> channels;
    private Integer recipientCount;
    private Integer successCount;
    private Integer failureCount;
    private UUID sentBy;
    private Instant sentAt;
    private Map<String, Object> metadata;
    private List<UUID> notificationIds;
}
