package dopaminelite.notifications.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * API response wrapper for paginated broadcasts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastListResponse {
    private List<BroadcastDto> items;
    private long total;
}
