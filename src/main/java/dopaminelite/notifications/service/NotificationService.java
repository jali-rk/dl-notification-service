package dopaminelite.notifications.service;

import dopaminelite.notifications.dto.*;
import dopaminelite.notifications.entity.BroadcastRecord;
import dopaminelite.notifications.entity.DeliveryOutbox;
import dopaminelite.notifications.entity.Notification;
import dopaminelite.notifications.entity.NotificationTemplate;
import dopaminelite.notifications.entity.enums.DeliveryStatus;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import dopaminelite.notifications.exception.ResourceNotFoundException;
import dopaminelite.notifications.exception.ValidationException;
import dopaminelite.notifications.repository.BroadcastRecordRepository;
import dopaminelite.notifications.repository.DeliveryOutboxRepository;
import dopaminelite.notifications.repository.NotificationRepository;
import dopaminelite.notifications.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final EmailService emailService;
    private final NotificationTemplateRepository templateRepository;
    private final BroadcastRecordRepository broadcastRepository;
    private final BffClientService bffClientService;
    
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
     * List all notifications for admin with optional filters.
     * Similar to listNotifications but without userId filter.
     * Parameters: unreadOnly, channel, limit, offset.
     */
    @Transactional(readOnly = true)
    public NotificationListResponse listAllNotifications(
        Boolean unreadOnly,
        NotificationChannel channel,
        int limit,
        int offset
    ) {
        Pageable pageable = PageRequest.of(offset / limit, limit);
        Page<Notification> page;

        boolean isUnread = unreadOnly != null && unreadOnly;

        if (channel != null && isUnread) {
            page = notificationRepository.findAllByIsReadAndChannelOrderByCreatedAtDesc(
                false, channel, pageable
            );
        } else if (channel != null) {
            page = notificationRepository.findAllByChannelOrderByCreatedAtDesc(
                channel, pageable
            );
        } else if (isUnread) {
            page = notificationRepository.findAllByIsReadOrderByCreatedAtDesc(
                false, pageable
            );
        } else {
            page = notificationRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        List<NotificationDto> items = page.getContent().stream()
            .map(this::toDto)
            .toList();

        return NotificationListResponse.builder()
            .items(items)
            .total(page.getTotalElements())
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

        // Fetch user details from BFF
        UserPublicDataDto userData = bffClientService.getUserPublicData(request.getPrimaryUserId());
        
        List<NotificationChannel> channels = request.getChannels();
        if (channels == null || channels.isEmpty()) {
            channels = getDefaultChannelsForEventType(request.getEventType());
        }
        
        // Validate that email exists for email channel
        if (channels.contains(NotificationChannel.EMAIL) && 
            (userData.getEmail() == null || userData.getEmail().isBlank())) {
            log.warn("User {} has no email address, will skip email notification", 
                request.getPrimaryUserId());
        }
        
        for (NotificationChannel channel : channels) {
            // Skip email channel if no email address
            if (channel == NotificationChannel.EMAIL && 
                (userData.getEmail() == null || userData.getEmail().isBlank())) {
                log.warn("Skipping email notification for user {} - no email address", 
                    request.getPrimaryUserId());
                continue;
            }
            createNotificationForChannel(request, channel, userData);
        }
    }
    
    /**
     * Send direct notifications to multiple users.
     * Supports multi-channel sends similar to event processing.
     * Creates a broadcast record to track the send operation.
     */
    @Transactional
    public UUID sendDirectNotifications(DirectNotificationSendRequest request, UUID sentBy) {
        log.info("Sending direct notifications to {} users via {} channels",
            request.getTargetUserIds().size(), request.getChannels());

        // Create broadcast record
        BroadcastRecord broadcast = createBroadcastRecord(
            null, // no template
            request.getTitle(),
            request.getBody(),
            request.getChannels(),
            request.getTargetUserIds().size(),
            sentBy,
            request.getMetadata()
        );

        int successCount = 0;
        int failureCount = 0;

        for (UUID userId : request.getTargetUserIds()) {
            try {
                // Fetch user data from BFF
                UserPublicDataDto userData = bffClientService.getUserPublicData(userId);
                
                // Validate that email exists for email channel
                if (request.getChannels().contains(NotificationChannel.EMAIL) && 
                    (userData.getEmail() == null || userData.getEmail().isBlank())) {
                    log.warn("User {} has no email address, skipping email notification", userId);
                    failureCount += (int) request.getChannels().stream()
                        .filter(ch -> ch == NotificationChannel.EMAIL)
                        .count();
                }
                
                for (NotificationChannel channel : request.getChannels()) {
                    try {
                        // Skip email channel if no email address
                        if (channel == NotificationChannel.EMAIL && 
                            (userData.getEmail() == null || userData.getEmail().isBlank())) {
                            continue;
                        }
                        createDirectNotification(userId, userData.getEmail(), channel, request, userData, broadcast.getId());
                        successCount++;
                    } catch (Exception e) {
                        log.error("Failed to create notification for user {} channel {}", userId, channel, e);
                        failureCount++;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to fetch user data for user {}", userId, e);
                failureCount += request.getChannels().size();
            }
        }
        
        // Update broadcast stats
        updateBroadcastStats(broadcast, successCount, failureCount);
        
        return broadcast.getId();
    }
    
    /**
     * Send direct notifications by email addresses.
     * Note: IN_APP notifications are not supported when sending by email only,
     * as we don't have user IDs. EMAIL channel is the primary use case.
     */
    @Transactional
    public UUID sendDirectNotificationsByEmail(DirectNotificationSendByEmailRequest request, UUID sentBy) {
        log.info("Sending direct notifications to {} email addresses via {} channels",
            request.getTargetEmails().size(), request.getChannels());

        // Validate channels - only EMAIL is truly supported for email-only sends
        if (request.getChannels().contains(NotificationChannel.IN_APP) || 
            request.getChannels().contains(NotificationChannel.WHATSAPP)) {
            throw new ValidationException("Only EMAIL channel is supported when sending by email addresses. " +
                "For IN_APP or WHATSAPP channels, use the /send endpoint with user IDs.");
        }

        // Create broadcast record
        BroadcastRecord broadcast = createBroadcastRecord(
            null, // no template
            request.getTitle(),
            request.getBody(),
            request.getChannels(),
            request.getTargetEmails().size(),
            sentBy,
            request.getMetadata()
        );

        int successCount = 0;
        int failureCount = 0;

        for (String email : request.getTargetEmails()) {
            try {
                for (NotificationChannel channel : request.getChannels()) {
                    try {
                        if (channel == NotificationChannel.EMAIL) {
                            createDirectNotificationByEmail(email, request, broadcast.getId());
                            successCount++;
                        }
                    } catch (Exception e) {
                        log.error("Failed to create notification for email {} channel {}", email, channel, e);
                        failureCount++;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process email {}", email, e);
                failureCount += request.getChannels().size();
            }
        }
        
        // Update broadcast stats
        updateBroadcastStats(broadcast, successCount, failureCount);
        
        return broadcast.getId();
    }
    
    /**
     * Send notifications using a template.
     * Supports placeholder replacement for personalized templates.
     */
    @Transactional
    public UUID sendFromTemplate(SendFromTemplateRequest request, UUID sentBy) {
        // Get template
        NotificationTemplate template = templateRepository.findById(request.getTemplateId())
            .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + request.getTemplateId()));

        // Determine channels (use request channels or template defaults)
        List<NotificationChannel> channels = request.getChannels() != null && !request.getChannels().isEmpty()
            ? request.getChannels()
            : template.getChannels();

        if (channels == null || channels.isEmpty()) {
            throw new ValidationException("No channels specified for template-based send");
        }

        log.info("Sending notifications from template {} to {} users via {} channels",
            template.getTemplateName(), request.getTargetUserIds().size(), channels);

        // Use English content as default (can be enhanced to support language selection)
        String contentTemplate = template.getContentEnglish() != null
            ? template.getContentEnglish()
            : template.getContentSinhala();

        if (contentTemplate == null || contentTemplate.isBlank()) {
            throw new ValidationException("Template has no content");
        }

        // Create broadcast record
        BroadcastRecord broadcast = createBroadcastRecord(
            template.getId(),
            template.getTemplateName(),
            contentTemplate,
            channels,
            request.getTargetUserIds().size(),
            sentBy,
            request.getPlaceholderData()
        );

        int successCount = 0;
        int failureCount = 0;

        for (UUID userId : request.getTargetUserIds()) {
            try {
                // Fetch user data from BFF
                UserPublicDataDto userData = bffClientService.getUserPublicData(userId);
                
                // Validate that email exists for email channel
                if (channels.contains(NotificationChannel.EMAIL) && 
                    (userData.getEmail() == null || userData.getEmail().isBlank())) {
                    log.warn("User {} has no email address, skipping email notification", userId);
                    failureCount += (int) channels.stream()
                        .filter(ch -> ch == NotificationChannel.EMAIL)
                        .count();
                }
                
                // Replace placeholders in content
                String personalizedContent = replacePlaceholders(contentTemplate, request.getPlaceholderData(), userData);
                
                for (NotificationChannel channel : channels) {
                    try {
                        // Skip email channel if no email address
                        if (channel == NotificationChannel.EMAIL && 
                            (userData.getEmail() == null || userData.getEmail().isBlank())) {
                            continue;
                        }
                        createTemplateNotification(userId, userData.getEmail(), channel, template.getTemplateName(), personalizedContent, broadcast.getId());
                        successCount++;
                    } catch (Exception e) {
                        log.error("Failed to create template notification for user {} channel {}", 
                            userId, channel, e);
                        failureCount++;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to fetch user data for user {}", userId, e);
                failureCount += channels.size();
            }
        }
        
        // Update broadcast and template stats
        updateBroadcastStats(broadcast, successCount, failureCount);
        incrementTemplateSentTimes(template);
        
        return broadcast.getId();
    }
    
    /**
     * Create a broadcast record.
     */
    private BroadcastRecord createBroadcastRecord(UUID templateId, String title, String body,
                                                  List<NotificationChannel> channels, int recipientCount,
                                                  UUID sentBy, Map<String, Object> metadata) {
        BroadcastRecord broadcast = new BroadcastRecord();
        broadcast.setTemplateId(templateId);
        broadcast.setTitle(title);
        broadcast.setBody(body);
        broadcast.setChannels(channels);
        broadcast.setRecipientCount(recipientCount);
        broadcast.setSuccessCount(0);
        broadcast.setFailureCount(0);
        broadcast.setSentBy(sentBy);
        broadcast.setSentAt(Instant.now());
        broadcast.setMetadata(metadata);
        
        broadcast = broadcastRepository.save(broadcast);
        log.info("Created broadcast record: {} for {} recipients", broadcast.getId(), recipientCount);
        
        return broadcast;
    }
    
    /**
     * Update broadcast statistics.
     */
    private void updateBroadcastStats(BroadcastRecord broadcast, int successCount, int failureCount) {
        broadcast.setSuccessCount(successCount);
        broadcast.setFailureCount(failureCount);
        broadcastRepository.save(broadcast);
        log.debug("Updated broadcast {} stats: success={}, failure={}", 
            broadcast.getId(), successCount, failureCount);
    }
    
    /**
     * Increment template sent times.
     */
    private void incrementTemplateSentTimes(NotificationTemplate template) {
        template.setSentTimes(template.getSentTimes() + 1);
        templateRepository.save(template);
    }
    
    /**
     * Replace placeholders in template content.
     * Supports {{placeholder}} syntax and automatically fetches dynamic user fields.
     * 
     * Dynamic fields from user object:
     * - {{name}} -> user.fullName
     * - {{email}} -> user.email
     * - {{registration}} -> user.codeNumber
     * - {{date}} -> today's date (formatted as dd)
     * - {{month}} -> current month name
     */
    private String replacePlaceholders(String content, Map<String, Object> placeholders, UserPublicDataDto user) {
        // Build combined placeholders map with user data
        Map<String, Object> combinedPlaceholders = new java.util.HashMap<>();
        
        // Add provided placeholders
        if (placeholders != null && !placeholders.isEmpty()) {
            combinedPlaceholders.putAll(placeholders);
        }
        
        // Extract dynamic user fields
        if (user != null) {
            // User name - use fullName field
            if (user.getFullName() != null) {
                combinedPlaceholders.putIfAbsent("name", user.getFullName());
            }
            
            // User email
            if (user.getEmail() != null) {
                combinedPlaceholders.putIfAbsent("email", user.getEmail());
            }
            
            // Registration code - use codeNumber field
            if (user.getCodeNumber() != null) {
                combinedPlaceholders.putIfAbsent("registration", user.getCodeNumber());
            }
        }
        
        // Current date and month
        java.time.LocalDate today = java.time.LocalDate.now();
        combinedPlaceholders.putIfAbsent("date", String.valueOf(today.getDayOfMonth()));
        
        java.time.Month currentMonth = today.getMonth();
        combinedPlaceholders.putIfAbsent("month", currentMonth.toString());
        
        // Replace all placeholders
        String result = content;
        Pattern pattern = Pattern.compile("\\{\\{([^}]+)\\}\\}");
        Matcher matcher = pattern.matcher(content);
        
        while (matcher.find()) {
            String placeholder = matcher.group(1).trim();
            Object value = combinedPlaceholders.get(placeholder);
            if (value != null) {
                result = result.replace("{{" + placeholder + "}}", value.toString());
            }
        }
        
        return result;
    }
    
    /**
     * Create a notification from a template.
     * If body contains HTML tags, sends as HTML email.
     */
    private void createTemplateNotification(UUID userId, String userEmail, NotificationChannel channel, 
                                           String title, String body, UUID broadcastId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setChannel(channel);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setDeliveryStatus(DeliveryStatus.PENDING);
        notification.setRead(false);
        notification.setBroadcastId(broadcastId);
        
        notificationRepository.save(notification);
        if (channel != NotificationChannel.IN_APP) {
            enqueueOutbox(notification, userEmail);
        }
    }
    
    // Helper methods
    
    /**
     * Create a notification for a given event and channel.
     */
    private void createNotificationForChannel(NotificationEventRequest request, NotificationChannel channel, UserPublicDataDto userData) {
        Notification notification = new Notification();
        notification.setUserId(request.getPrimaryUserId());
        notification.setChannel(channel);
        
        // Generate title and body based on event type
        String[] content = generateContentForEvent(request);
        String title = content[0];
        String body = content[1];
        
        // Replace placeholders in title and body with actual user data
        title = replacePlaceholders(title, request.getPayload(), userData);
        body = replacePlaceholders(body, request.getPayload(), userData);
        
        notification.setTitle(title);
        notification.setBody(body);
        
        notification.setTemplateKey(request.getEventType().name());
        notification.setMetadata(request.getPayload());
        notification.setDeliveryStatus(DeliveryStatus.PENDING);
        notification.setRead(false);
        
        notificationRepository.save(notification);
        if (channel != NotificationChannel.IN_APP) {
            // enqueue to outbox for async delivery
            enqueueOutbox(notification, userData.getEmail());
        }
    }
    
    /**
     * Create a direct ad-hoc notification for a user and channel.
     */
    private void createDirectNotification(UUID userId, String userEmail, NotificationChannel channel, 
                                         DirectNotificationSendRequest request, UserPublicDataDto userData, UUID broadcastId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setChannel(channel);
        
        // Replace placeholders in title and body with actual user data
        String title = replacePlaceholders(request.getTitle(), request.getMetadata(), userData);
        String body = replacePlaceholders(request.getBody(), request.getMetadata(), userData);
        
        notification.setTitle(title);
        notification.setBody(body);
        notification.setMetadata(request.getMetadata());
        notification.setDeliveryStatus(DeliveryStatus.PENDING);
        notification.setRead(false);
        notification.setBroadcastId(broadcastId);
        
        notificationRepository.save(notification);
        if (channel != NotificationChannel.IN_APP) {
            // enqueue to outbox for async delivery
            enqueueOutbox(notification, userEmail);
        }
    }
    
    /**
     * Create a direct notification by email address only.
     * Since we don't have a user ID, we skip saving to notifications table
     * and directly send via email service (no outbox, immediate delivery).
     * Automatically detects HTML content and sends as rich text when appropriate.
     */
    private void createDirectNotificationByEmail(String email, DirectNotificationSendByEmailRequest request, UUID broadcastId) {
        // Skip saving to notifications table since user_id is required
        // Directly send email without outbox processing
        try {
            // Detect if body contains HTML tags
            boolean isHtml = request.getBody() != null && request.getBody().matches("(?i).*<[a-z].*>.*");
            
            if (isHtml) {
                emailService.sendHtmlEmail(email, request.getTitle(), request.getBody());
            } else {
                emailService.sendEmail(email, request.getTitle(), request.getBody());
            }
            log.info("Sent email directly to {}", email);
        } catch (Exception e) {
            log.error("Failed to send email to {}", email, e);
            throw e;
        }
    }
    
    /**
     * Generate title/body content for an event.
     * Templates can include placeholders like {{name}}, {{email}}, {{registration}} for user data,
     * and custom placeholders from the payload will be replaced.
     */
    private String[] generateContentForEvent(NotificationEventRequest request) {
        // This is a simplified version. In production, use proper templating engine
        String title;
        String body;
        
        switch (request.getEventType()) {
            case PAYMENT_STATUS_CHANGED:
                title = "Payment Status Updated";
                body = "Hi {{name}}, your payment status has been changed to {{newStatus}}.";
                break;
                
            case ISSUE_STATUS_CHANGED:
                title = "Issue Status Updated";
                body = "Hi {{name}}, your issue status has been changed to {{newStatus}}.";
                break;
                
            case ISSUE_MESSAGE_NEW:
                title = "New Message";
                body = "Hi {{name}}, you have a new message: {{messagePreview}}";
                break;
                
            case STUDENT_VERIFIED:
                title = "Account Verified";
                body = "Welcome to DopamineLite, {{name}}! Your account has been verified.";
                break;
                
            case STUDENT_REGISTERED:
                title = "Registration Successful";
                body = "Thank you for registering with DopamineLite, {{name}}. Your registration number is {{registration}}.";
                break;
                
            case ADMIN_BROADCAST:
                title = "{{title}}";
                body = "{{message}}";
                break;
                
            default:
                title = "Notification";
                body = "Hi {{name}}, you have a new notification.";
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
     * Automatically detects HTML content and sends as rich text when appropriate.
     * TODO: Integrate email/WhatsApp providers; update outbox status.
     */
    public void deliverNotification(Notification notification, String recipientEmail) {
        // TODO: Implement actual delivery logic for EMAIL and WHATSAPP
        // This would integrate with email service (e.g., SendGrid, AWS SES)
        // and WhatsApp service (e.g., Twilio, WhatsApp Business API)
        
        log.info("Delivering notification {} via {}", 
            notification.getId(), notification.getChannel());
        
        try {
            switch (notification.getChannel()) {
                case EMAIL:
                    // Detect if body contains HTML tags
                    boolean isHtml = notification.getBody() != null && 
                        notification.getBody().matches("(?i).*<[a-z].*>.*");
                    
                    if (isHtml) {
                        emailService.sendHtmlEmail(recipientEmail, notification.getTitle(), notification.getBody());
                    } else {
                        emailService.sendEmail(recipientEmail, notification.getTitle(), notification.getBody());
                    }
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
    private void enqueueOutbox(Notification notification, String recipientEmail) {
        DeliveryOutbox outbox = new DeliveryOutbox();
        outbox.setNotificationId(notification.getId());
        outbox.setChannel(notification.getChannel());
        outbox.setRecipientEmail(recipientEmail);
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
