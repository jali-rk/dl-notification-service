package dopaminelite.notifications.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * API response wrapper for paginated templates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateListResponse {
    private List<TemplateDto> items;
    private long total;
}
