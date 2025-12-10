package dopaminelite.notifications.controller;

import dopaminelite.notifications.dto.DirectNotificationSendRequest;
import dopaminelite.notifications.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
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
     */
    @PostMapping("/send")
    public ResponseEntity<Void> sendDirectNotifications(
        @Valid @RequestBody DirectNotificationSendRequest request
    ) {
        notificationService.sendDirectNotifications(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
