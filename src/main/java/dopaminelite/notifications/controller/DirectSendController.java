package dopaminelite.notifications.controller;

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
        @RequestHeader(value = "X-User-Id", required = false) UUID sentBy,
        @RequestHeader(value = "Authorization", required = true) String authorization
    ) {
        // Default to system user if not provided
        UUID sender = sentBy != null ? sentBy : UUID.fromString("00000000-0000-0000-0000-000000000000");
        
        // Extract bearer token from Authorization header
        String bearerToken = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        
        UUID broadcastId = notificationService.sendDirectNotifications(request, sender, bearerToken);
        
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
        @RequestHeader(value = "X-User-Id", required = false) UUID sentBy,
        @RequestHeader(value = "Authorization", required = true) String authorization
    ) {
        // Default to system user if not provided
        UUID sender = sentBy != null ? sentBy : UUID.fromString("00000000-0000-0000-0000-000000000000");
        
        // Extract bearer token from Authorization header
        String bearerToken = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        
        UUID broadcastId = notificationService.sendFromTemplate(request, sender, bearerToken);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            Map.of("broadcastId", broadcastId, "message", "Template-based send request accepted")
        );
    }
}
