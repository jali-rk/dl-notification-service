package dopaminelite.notifications.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for sending notifications from a template.
 * User details are fetched from BFF's /users/{userId}/public endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SendFromTemplateRequest {
    
    @NotNull(message = "Template ID is required")
    private UUID templateId;
    
    @NotEmpty(message = "Target user IDs list cannot be empty")
    private List<UUID> targetUserIds;
    
    private Map<String, Object> placeholderData;
    
    private List<NotificationChannel> channels;
}
