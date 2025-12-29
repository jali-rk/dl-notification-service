package dopaminelite.notifications.service;

import dopaminelite.notifications.dto.BroadcastDto;
import dopaminelite.notifications.dto.BroadcastListResponse;
import dopaminelite.notifications.entity.BroadcastRecord;
import dopaminelite.notifications.entity.Notification;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import dopaminelite.notifications.exception.ResourceNotFoundException;
import dopaminelite.notifications.repository.BroadcastRecordRepository;
import dopaminelite.notifications.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing broadcast records.
 * Tracks broadcast notification send operations and provides history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastService {
    
    private final BroadcastRecordRepository broadcastRepository;
    private final NotificationRepository notificationRepository;
    
    /**
     * List broadcasts with optional filters and pagination.
     */
    @Transactional(readOnly = true)
    public BroadcastListResponse listBroadcasts(UUID sentBy, Instant dateFrom, Instant dateTo,
                                                String search, int limit, int offset) {
        // Use database column name for native query sorting
        Pageable pageable = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "sent_at"));

        Page<BroadcastRecord> page = broadcastRepository.findByFilters(sentBy, dateFrom, dateTo, search, pageable);

        return BroadcastListResponse.builder()
            .items(page.getContent().stream().map(this::toDtoWithoutNotifications).toList())
            .total(page.getTotalElements())
            .build();
    }
    
    /**
     * Get a broadcast by ID.
     */
    @Transactional(readOnly = true)
    public BroadcastDto getBroadcast(UUID broadcastId) {
        BroadcastRecord broadcast = broadcastRepository.findById(broadcastId)
            .orElseThrow(() -> new ResourceNotFoundException("Broadcast not found: " + broadcastId));
        return toDtoWithNotifications(broadcast);
    }
    
    /**
     * Create a broadcast record.
     */
    @Transactional
    public BroadcastDto createBroadcast(UUID templateId, String title, String body, 
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
        
        return toDtoWithoutNotifications(broadcast);
    }
    
    /**
     * Update broadcast statistics.
     */
    @Transactional
    public void updateBroadcastStats(UUID broadcastId, int successCount, int failureCount) {
        BroadcastRecord broadcast = broadcastRepository.findById(broadcastId).orElse(null);
        if (broadcast != null) {
            broadcast.setSuccessCount(successCount);
            broadcast.setFailureCount(failureCount);
            broadcastRepository.save(broadcast);
            log.debug("Updated broadcast {} stats: success={}, failure={}", 
                broadcastId, successCount, failureCount);
        }
    }
    
    /**
     * Map entity to DTO without notification IDs (for list view).
     */
    private BroadcastDto toDtoWithoutNotifications(BroadcastRecord broadcast) {
        return BroadcastDto.builder()
            .id(broadcast.getId())
            .templateId(broadcast.getTemplateId())
            .title(broadcast.getTitle())
            .body(broadcast.getBody())
            .channels(broadcast.getChannels())
            .recipientCount(broadcast.getRecipientCount())
            .successCount(broadcast.getSuccessCount())
            .failureCount(broadcast.getFailureCount())
            .sentBy(broadcast.getSentBy())
            .sentAt(broadcast.getSentAt())
            .metadata(broadcast.getMetadata())
            .build();
    }
    
    /**
     * Map entity to DTO with notification IDs (for detail view).
     */
    private BroadcastDto toDtoWithNotifications(BroadcastRecord broadcast) {
        // Fetch all notification IDs for this broadcast
        List<UUID> notificationIds = notificationRepository.findByBroadcastId(broadcast.getId())
            .stream()
            .map(Notification::getId)
            .collect(Collectors.toList());
        
        return BroadcastDto.builder()
            .id(broadcast.getId())
            .templateId(broadcast.getTemplateId())
            .title(broadcast.getTitle())
            .body(broadcast.getBody())
            .channels(broadcast.getChannels())
            .recipientCount(broadcast.getRecipientCount())
            .successCount(broadcast.getSuccessCount())
            .failureCount(broadcast.getFailureCount())
            .sentBy(broadcast.getSentBy())
            .sentAt(broadcast.getSentAt())
            .metadata(broadcast.getMetadata())
            .notificationIds(notificationIds)
            .build();
    }
}
