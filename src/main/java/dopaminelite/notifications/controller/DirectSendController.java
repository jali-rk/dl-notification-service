package dopaminelite.notifications.controller;

import dopaminelite.notifications.dto.DirectNotificationSendByEmailRequest;
import dopaminelite.notifications.dto.DirectNotificationSendRequest;
import dopaminelite.notifications.dto.SendFromTemplateRequest;
import dopaminelite.notifications.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Validated
public class DirectSendController {
    
    private final NotificationService notificationService;
    
    /**
     * Directly send ad-hoc notifications.
     *
     * OpenAPI: POST /notifications/send
     * Behavior: Accepts one or more channels (IN_APP, EMAIL, WHATSAPP)
     * and sends to provided targetUserIds. Used by admins via BFF for
     * broadcasts and system messages.
     *
     * Returns the broadcast ID for tracking.
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendDirectNotifications(
        @Valid @RequestBody DirectNotificationSendRequest request,
        @RequestHeader(value = "X-User-Id", required = false) UUID sentBy
    ) {
        // Default to system user if not provided
        UUID sender = sentBy != null ? sentBy : UUID.fromString("00000000-0000-0000-0000-000000000000");

        UUID broadcastId = notificationService.sendDirectNotifications(request, sender);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            Map.of("broadcastId", broadcastId, "message", "Send request accepted")
        );
    }

    /**
     * Send notification using template.
     *
     * OpenAPI: POST /notifications/send-from-template
     * Supports placeholder replacement for personalized templates.
     *
     * Returns the broadcast ID for tracking.
     */
    @PostMapping("/send-from-template")
    public ResponseEntity<Map<String, Object>> sendFromTemplate(
        @Valid @RequestBody SendFromTemplateRequest request,
        @RequestHeader(value = "X-User-Id", required = false) UUID sentBy
    ) {
        // Default to system user if not provided
        UUID sender = sentBy != null ? sentBy : UUID.fromString("00000000-0000-0000-0000-000000000000");

        UUID broadcastId = notificationService.sendFromTemplate(request, sender);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            Map.of("broadcastId", broadcastId, "message", "Template-based send request accepted")
        );
    }

    /**
     * Directly send ad-hoc notifications by email addresses.
     *
     * OpenAPI: POST /notifications/send-by-email
     * Behavior: Accepts email addresses directly instead of user IDs.
     * Only EMAIL channel is supported (IN_APP requires user IDs).
     * Used for sending notifications to email recipients without needing user accounts.
     *
     * Returns the broadcast ID for tracking.
     */
    @PostMapping("/send-by-email")
    public ResponseEntity<Map<String, Object>> sendDirectNotificationsByEmail(
        @Valid @RequestBody DirectNotificationSendByEmailRequest request,
        @RequestHeader(value = "X-User-Id", required = false) UUID sentBy
    ) {
        // Default to system user if not provided
        UUID sender = sentBy != null ? sentBy : UUID.fromString("00000000-0000-0000-0000-000000000000");

        UUID broadcastId = notificationService.sendDirectNotificationsByEmail(request, sender);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            Map.of("broadcastId", broadcastId, "message", "Send by email request accepted")
        );
    }
}
