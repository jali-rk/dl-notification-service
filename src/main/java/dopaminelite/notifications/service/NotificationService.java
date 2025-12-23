package dopaminelite.notifications.service;

import dopaminelite.notifications.client.UserServiceClient;
import dopaminelite.notifications.dto.*;
import dopaminelite.notifications.entity.DeliveryOutbox;
import dopaminelite.notifications.entity.Notification;
import dopaminelite.notifications.entity.enums.DeliveryStatus;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import dopaminelite.notifications.exception.ResourceNotFoundException;
import dopaminelite.notifications.repository.DeliveryOutboxRepository;
import dopaminelite.notifications.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Core business logic for notifications.
 *
 * Responsibilities:
 * - Query lists and single notifications for BFF use.
 * - Mark notifications as read.
 * - Process domain events and fan out to channels (IN_APP, EMAIL, WHATSAPP).
 * - Handle direct, ad-hoc sends across multiple channels.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    private final DeliveryOutboxRepository deliveryOutboxRepository;
    private final SesEmailService sesEmailService;
    private final UserServiceClient userServiceClient;
    
    /**
     * List notifications for a user with optional filters.
     * Parameters mirror OpenAPI query: unreadOnly, channel, limit, offset.
     */
    @Transactional(readOnly = true)
    public NotificationListResponse listNotifications(
        UUID userId,
        Boolean unreadOnly,
        NotificationChannel channel,
        int limit,
        int offset
    ) {
        Pageable pageable = PageRequest.of(offset / limit, limit);
        Page<Notification> page;
        long total;
        
        boolean isUnread = unreadOnly != null && unreadOnly;
        
        if (channel != null && isUnread) {
            page = notificationRepository.findByUserIdAndIsReadAndChannelOrderByCreatedAtDesc(
                userId, false, channel, pageable
            );
            total = notificationRepository.countByUserIdAndIsReadAndChannel(userId, false, channel);
        } else if (channel != null) {
            page = notificationRepository.findByUserIdAndChannelOrderByCreatedAtDesc(
                userId, channel, pageable
            );
            total = notificationRepository.countByUserIdAndChannel(userId, channel);
        } else if (isUnread) {
            page = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(
                userId, false, pageable
            );
            total = notificationRepository.countByUserIdAndIsRead(userId, false);
        } else {
            page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
            total = notificationRepository.countByUserId(userId);
        }
        
        List<NotificationDto> items = page.getContent().stream()
            .map(this::toDto)
            .toList();
        
        return NotificationListResponse.builder()
            .items(items)
            .total(total)
            .build();
    }
    
    /**
     * Get a single notification by ID.
     */
    @Transactional(readOnly = true)
    public NotificationDto getNotification(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        return toDto(notification);
    }
    
    /**
     * Mark a notification as read.
     * If setting read=true, populates readAt with current timestamp.
     */
    @Transactional
    public NotificationDto markAsRead(UUID notificationId, NotificationReadUpdateRequest request) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        
        notification.setRead(request.isRead());
        if (request.isRead() && notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        } else if (!request.isRead()) {
            notification.setReadAt(null);
        }
        
        notification = notificationRepository.save(notification);
        return toDto(notification);
    }
    
    /**
     * Process a notification event.
     * Applies event-specific templates and creates notifications per channel.
     * Non-IN_APP channels are queued for delivery via outbox (future work).
     */
    @Transactional
    public void processNotificationEvent(NotificationEventRequest request) {
        log.info("Processing notification event: {} for user: {}", 
            request.getEventType(), request.getPrimaryUserId());
        
        List<NotificationChannel> channels = request.getChannels();
        if (channels == null || channels.isEmpty()) {
            channels = getDefaultChannelsForEventType(request.getEventType());
        }
        
        for (NotificationChannel channel : channels) {
            createNotificationForChannel(request, channel);
        }
    }
    
    /**
     * Send direct notifications to multiple users.
     * Supports multi-channel sends similar to event processing.
     */
    @Transactional
    public void sendDirectNotifications(DirectNotificationSendRequest request) {
        log.info("Sending direct notifications to {} users via {} channels", 
            request.getTargetUserIds().size(), request.getChannels());
        
        for (UUID userId : request.getTargetUserIds()) {
            for (NotificationChannel channel : request.getChannels()) {
                createDirectNotification(userId, channel, request);
            }
        }
    }
    
    // Helper methods
    
    /**
     * Create a notification for a given event and channel.
     */
    private void createNotificationForChannel(NotificationEventRequest request, NotificationChannel channel) {
        Notification notification = new Notification();
        notification.setUserId(request.getPrimaryUserId());
        notification.setChannel(channel);
        
        // Generate title and body based on event type
        String[] content = generateContentForEvent(request);
        notification.setTitle(content[0]);
        notification.setBody(content[1]);
        
        notification.setTemplateKey(request.getEventType().name());
        notification.setMetadata(request.getPayload());
        notification.setDeliveryStatus(DeliveryStatus.PENDING);
        notification.setRead(false);
        
        notificationRepository.save(notification);
        if (channel != NotificationChannel.IN_APP) {
            // enqueue to outbox for async delivery
            enqueueOutbox(notification);
        }
        
        // For non IN_APP channels, trigger actual delivery (email, WhatsApp)
        if (channel != NotificationChannel.IN_APP) {
            deliverNotification(notification);
        }
    }
    
    /**
     * Create a direct ad-hoc notification for a user and channel.
     */
    private void createDirectNotification(UUID userId, NotificationChannel channel, DirectNotificationSendRequest request) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setChannel(channel);
        notification.setTitle(request.getTitle());
        notification.setBody(request.getBody());
        notification.setMetadata(request.getMetadata());
        notification.setDeliveryStatus(DeliveryStatus.PENDING);
        notification.setRead(false);
        
        notificationRepository.save(notification);
        if (channel != NotificationChannel.IN_APP) {
            // enqueue to outbox for async delivery
            enqueueOutbox(notification);
        }
        
        // For non IN_APP channels, trigger actual delivery
        if (channel != NotificationChannel.IN_APP) {
            deliverNotification(notification);
        }
    }
    
    /**
     * Generate title/body content for an event.
     * In production, replace with proper templating.
     */
    private String[] generateContentForEvent(NotificationEventRequest request) {
        // This is a simplified version. In production, use proper templating engine
        String title;
        String body;
        
        switch (request.getEventType()) {
            case PAYMENT_STATUS_CHANGED:
                String newStatus = request.getPayload() != null 
                    ? String.valueOf(request.getPayload().get("newStatus")) 
                    : "updated";
                title = "Payment Status Updated";
                body = String.format("Your payment status has been changed to %s.", newStatus);
                break;
                
            case ISSUE_STATUS_CHANGED:
                String issueStatus = request.getPayload() != null 
                    ? String.valueOf(request.getPayload().get("newStatus")) 
                    : "updated";
                title = "Issue Status Updated";
                body = String.format("Your issue status has been changed to %s.", issueStatus);
                break;
                
            case ISSUE_MESSAGE_NEW:
                String preview = request.getPayload() != null 
                    ? String.valueOf(request.getPayload().getOrDefault("messagePreview", "...")) 
                    : "...";
                title = "New Message";
                body = String.format("You have a new message: %s", preview);
                break;
                
            case STUDENT_VERIFIED:
                title = "Account Verified";
                body = "Welcome to DopamineLite! Your account has been verified.";
                break;
                
            case STUDENT_REGISTERED:
                title = "Registration Successful";
                body = "Thank you for registering with DopamineLite.";
                break;
                
            case ADMIN_BROADCAST:
                title = request.getPayload() != null 
                    ? String.valueOf(request.getPayload().getOrDefault("title", "Announcement")) 
                    : "Announcement";
                body = request.getPayload() != null 
                    ? String.valueOf(request.getPayload().getOrDefault("message", "")) 
                    : "";
                break;
                
            default:
                title = "Notification";
                body = "You have a new notification.";
        }
        
        return new String[]{title, body};
    }
    
    /**
     * Default channels per event type.
     */
    private List<NotificationChannel> getDefaultChannelsForEventType(
        dopaminelite.notifications.entity.enums.NotificationEventType eventType
    ) {
        // Default channel configuration per event type
        return switch (eventType) {
            case PAYMENT_STATUS_CHANGED -> List.of(NotificationChannel.EMAIL, NotificationChannel.IN_APP);
            case ISSUE_STATUS_CHANGED -> List.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL);
            case ISSUE_MESSAGE_NEW -> List.of(NotificationChannel.IN_APP);
            case STUDENT_VERIFIED -> List.of(NotificationChannel.EMAIL);
            case STUDENT_REGISTERED -> List.of(NotificationChannel.EMAIL);
            case ADMIN_BROADCAST -> List.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL);
        };
    }
    
    /**
     * Deliver via external providers (stub).
     * TODO: Integrate email/WhatsApp providers; update outbox status.
     */
    public void deliverNotification(Notification notification) {
        // TODO: Implement actual delivery logic for EMAIL and WHATSAPP
        // This would integrate with email service (e.g., SendGrid, AWS SES)
        // and WhatsApp service (e.g., Twilio, WhatsApp Business API)
        
        log.info("Delivering notification {} via {}", 
            notification.getId(), notification.getChannel());
        
        try {
            switch (notification.getChannel()) {
                case EMAIL:
                    String recipientEmail = userServiceClient.getUserEmail(notification.getUserId());
                    sesEmailService.sendEmail(recipientEmail, notification.getTitle(), notification.getBody());
                    break;
                case WHATSAPP:
                    // TODO: Integrate WhatsApp provider
                    log.info("WhatsApp delivery stub for user {}", notification.getUserId());
                    break;
                default:
                    break;
            }
            
            notification.setDeliveryStatus(DeliveryStatus.SENT);
        } catch (Exception e) {
            log.error("Failed to deliver notification {}", notification.getId(), e);
            notification.setDeliveryStatus(DeliveryStatus.FAILED);
        }
        
        notificationRepository.save(notification);
    }

    /**
     * Enqueue a delivery record into delivery_outbox for async processing.
     * The worker will pick it up and handle retries.
     */
    private void enqueueOutbox(Notification notification) {
        DeliveryOutbox outbox = new DeliveryOutbox();
        outbox.setNotificationId(notification.getId());
        outbox.setChannel(notification.getChannel());
        outbox.setStatus(DeliveryStatus.PENDING);
        outbox.setRetryCount(0);
        outbox.setMaxRetries(3);
        outbox.setNextRetryAt(Instant.now());
        deliveryOutboxRepository.save(outbox);
        log.debug("Enqueued outbox entry for notification {} via {}", notification.getId(), notification.getChannel());
    }
    
    /**
     * Map entity to DTO for API responses.
     */
    private NotificationDto toDto(Notification notification) {
        return NotificationDto.builder()
            .id(notification.getId())
            .userId(notification.getUserId())
            .channel(notification.getChannel())
            .title(notification.getTitle())
            .body(notification.getBody())
            .isRead(notification.isRead())
            .createdAt(notification.getCreatedAt())
            .readAt(notification.getReadAt())
            .deliveryStatus(notification.getDeliveryStatus())
            .templateKey(notification.getTemplateKey())
            .metadata(notification.getMetadata())
            .build();
    }
}
