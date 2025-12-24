package dopaminelite.notifications.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Direct ad-hoc send request, typically initiated by admins via BFF.
 * Supports multi-channel sends to a set of target users.
 * User details are fetched from BFF's /users/{userId}/public endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DirectNotificationSendRequest {
    
    @NotEmpty(message = "Target user IDs are required")
    private List<UUID> targetUserIds;
    
    @NotEmpty(message = "At least one channel is required")
    private List<NotificationChannel> channels;
    
    @NotBlank(message = "Title is required")
    private String title;
    
    @NotBlank(message = "Body is required")
    private String body;
    
    private Map<String, Object> metadata;
}
