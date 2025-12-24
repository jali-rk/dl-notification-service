package dopaminelite.notifications.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import dopaminelite.notifications.entity.enums.TemplateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for creating a notification template.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateTemplateRequest {
    
    @NotBlank(message = "Template ID is required")
    private String templateId;
    
    @NotBlank(message = "Template name is required")
    private String templateName;
    
    @NotNull(message = "Template type is required")
    private TemplateType type;
    
    private String contentSinhala;
    private String contentEnglish;
    private List<NotificationChannel> channels;
    private Map<String, Object> metadata;
}
