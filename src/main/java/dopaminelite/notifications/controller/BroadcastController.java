package dopaminelite.notifications.controller;

import dopaminelite.notifications.dto.BroadcastDto;
import dopaminelite.notifications.dto.BroadcastListResponse;
import dopaminelite.notifications.dto.NotificationListResponse;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import dopaminelite.notifications.service.BroadcastService;
import dopaminelite.notifications.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * REST controller for broadcast notification history.
 *
 * Endpoints:
 * - GET /broadcasts - List all notifications (admin view)
 * - GET /broadcasts/{id} - Get broadcast details
 */
@RestController
@RequestMapping("/broadcasts")
@RequiredArgsConstructor
@Validated
public class BroadcastController {

    private final BroadcastService broadcastService;
    private final NotificationService notificationService;
    
    /**
     * List all notifications (admin view).
     *
     * Modified to return notifications instead of broadcast records.
     * OpenAPI: GET /broadcasts
     * Accepts original broadcast filters for compatibility but returns notifications.
     */
    @GetMapping
    public ResponseEntity<NotificationListResponse> listBroadcasts(
        @RequestParam(required = false) UUID sentBy,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
        @RequestParam(required = false) String search,
        @RequestParam(required = false, defaultValue = "20") @Min(1) @Max(100) Integer limit,
        @RequestParam(required = false, defaultValue = "0") @Min(0) Integer offset
    ) {
        // Return all notifications instead of broadcast records
        // Ignore broadcast-specific filters (sentBy, dateFrom, dateTo, search) for now
        NotificationListResponse response = notificationService.listAllNotifications(
            false, // unreadOnly
            null,  // channel
            limit,
            offset
        );
        return ResponseEntity.ok(response);
    }

    // COMMENTED OUT: Original broadcast records logic
    /*
    @GetMapping
    public ResponseEntity<BroadcastListResponse> listBroadcasts(
        @RequestParam(required = false) UUID sentBy,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dateTo,
        @RequestParam(required = false) String search,
        @RequestParam(required = false, defaultValue = "20") @Min(1) @Max(100) Integer limit,
        @RequestParam(required = false, defaultValue = "0") @Min(0) Integer offset
    ) {
        BroadcastListResponse response = broadcastService.listBroadcasts(
            sentBy, dateFrom, dateTo, search, limit, offset
        );
        return ResponseEntity.ok(response);
    }
    */
    
    /**
     * Get broadcast details.
     * 
     * OpenAPI: GET /broadcasts/{broadcastId}
     */
    @GetMapping("/{broadcastId}")
    public ResponseEntity<BroadcastDto> getBroadcast(@PathVariable UUID broadcastId) {
        BroadcastDto broadcast = broadcastService.getBroadcast(broadcastId);
        return ResponseEntity.ok(broadcast);
    }
}
