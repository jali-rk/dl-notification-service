package dopaminelite.notifications.controller;

import dopaminelite.notifications.dto.NotificationEventRequest;
import dopaminelite.notifications.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notification-events")
@RequiredArgsConstructor
@Validated
public class NotificationEventController {
    
    private final NotificationService notificationService;
    
    /**
     * Ingest a domain event to trigger notifications.
     *
     * OpenAPI: POST /notification-events
     * Behavior: Applies templates by eventType, creates IN_APP records,
     * and initiates EMAIL/WHATSAPP via providers as configured.
     * Example payloads include payment status changes, issue updates,
     * new issue messages, and student verification.
     */
    @PostMapping
    public ResponseEntity<Void> processNotificationEvent(
        @Valid @RequestBody NotificationEventRequest request
    ) {
        notificationService.processNotificationEvent(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
