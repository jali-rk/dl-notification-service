package dopaminelite.notifications.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import dopaminelite.notifications.entity.enums.TemplateType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for notification template.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateDto {
    private UUID id;
    private String templateId;
    private String templateName;
    private TemplateType type;
    private String contentSinhala;
    private String contentEnglish;
    private List<NotificationChannel> channels;
    private Map<String, Object> metadata;
    private Integer sentTimes;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
