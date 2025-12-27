package dopaminelite.notifications.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Direct ad-hoc send request using email addresses instead of user IDs.
 * Supports multi-channel sends to a set of target email addresses.
 * Note: Only EMAIL and IN_APP channels are supported when sending by email.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DirectNotificationSendByEmailRequest {
    
    @NotEmpty(message = "Target emails are required")
    private List<@Email(message = "Each email must be valid") String> targetEmails;
    
    @NotEmpty(message = "At least one channel is required")
    private List<NotificationChannel> channels;
    
    @NotBlank(message = "Title is required")
    private String title;
    
    @NotBlank(message = "Body is required")
    private String body;
    
    private Map<String, Object> metadata;
}
