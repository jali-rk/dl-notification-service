package dopaminelite.notifications.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for marking notifications as read/unread.
 * Defaults to isRead=true when omitted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationReadUpdateRequest {
    @Builder.Default
    private boolean isRead = true;
}
