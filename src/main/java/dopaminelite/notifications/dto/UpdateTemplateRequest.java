package dopaminelite.notifications.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import dopaminelite.notifications.entity.enums.TemplateType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for updating a notification template.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateTemplateRequest {
    private String templateName;
    private TemplateType type;
    private String contentSinhala;
    private String contentEnglish;
    private List<NotificationChannel> channels;
    private Map<String, Object> metadata;
}
