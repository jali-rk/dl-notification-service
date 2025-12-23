package dopaminelite.notifications.controller;

import dopaminelite.notifications.dto.NotificationDto;
import dopaminelite.notifications.dto.NotificationListResponse;
import dopaminelite.notifications.dto.NotificationReadUpdateRequest;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import dopaminelite.notifications.exception.ValidationException;
import dopaminelite.notifications.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {
    
    private final NotificationService notificationService;
    
    /**
        * List notifications for a user.
        *
        * OpenAPI: GET /notifications
        * Purpose: Used by BFF to show in-app notifications in MFEs.
        * Query params:
        *  - userId (UUID, required): Current user
        *  - unreadOnly (boolean, optional, default false): Filter unread
        *  - channel (IN_APP|EMAIL|WHATSAPP, optional): Filter by channel
        *  - limit (1..100, default 20) and offset (>=0, default 0): pagination
     */
    @GetMapping
    public ResponseEntity<NotificationListResponse> listNotifications(
        @RequestParam UUID userId,
        @RequestParam(required = false, defaultValue = "false") Boolean unreadOnly,
        @RequestParam(required = false) NotificationChannel channel,
        @RequestParam(required = false, defaultValue = "20") @Min(1) @Max(100) Integer limit,
        @RequestParam(required = false, defaultValue = "0") @Min(0) Integer offset
    ) {
        NotificationListResponse response = notificationService.listNotifications(
            userId, unreadOnly, channel, limit, offset
        );
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get a single notification.
     *
     * OpenAPI: GET /notifications/{notificationId}
     * Purpose: Optional detail view for debugging or deep linking.
     */
    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationDto> getNotification(
        @PathVariable UUID notificationId
    ) {
        if (notificationId == null) {
            throw new ValidationException("Notification ID cannot be empty");
        }
        
        NotificationDto notification = notificationService.getNotification(notificationId);
        return ResponseEntity.ok(notification);
    }
    
    /**
     * Mark a notification as read.
     *
     * OpenAPI: PATCH /notifications/{notificationId}/read
     * Behavior: Sets isRead=true and readAt=now for IN_APP notifications.
     * Request body optional; defaults to {"isRead": true}.
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationDto> markAsRead(
        @PathVariable UUID notificationId,
        @RequestBody(required = false) NotificationReadUpdateRequest request
    ) {
        if (request == null) {
            request = NotificationReadUpdateRequest.builder().isRead(true).build();
        }
        
        NotificationDto notification = notificationService.markAsRead(notificationId, request);
        return ResponseEntity.ok(notification);
    }
}
