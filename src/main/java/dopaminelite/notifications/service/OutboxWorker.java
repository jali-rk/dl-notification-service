package dopaminelite.notifications.service;

import dopaminelite.notifications.client.UserServiceClient;
import dopaminelite.notifications.entity.DeliveryOutbox;
import dopaminelite.notifications.entity.Notification;
import dopaminelite.notifications.entity.NotificationChannel;
import dopaminelite.notifications.entity.DeliveryStatus;
import dopaminelite.notifications.repository.DeliveryOutboxRepository;
import dopaminelite.notifications.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

/**
 * Scheduled worker to process pending deliveries from delivery_outbox.
 * Handles retries with exponential backoff.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWorker {

    private final DeliveryOutboxRepository outboxRepository;
    private final NotificationRepository notificationRepository;
    private final SesEmailService sesEmailService;
    private final UserServiceClient userServiceClient;

    /**
     * Polls pending/failed outbox entries and attempts delivery.
     * Runs every minute.
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void processPending() {
        List<DeliveryOutbox> pending = outboxRepository.findByStatusInAndNextRetryAtBefore(
            List.of(DeliveryStatus.PENDING, DeliveryStatus.FAILED),
            Instant.now()
        );
        if (pending.isEmpty()) return;
        
        log.info("OutboxWorker: processing {} pending deliveries", pending.size());
        
        for (DeliveryOutbox outbox : pending) {
            processOutboxEntry(outbox);
        }
    }

    private void processOutboxEntry(DeliveryOutbox outbox) {
        Notification notification = notificationRepository.findById(outbox.getNotificationId())
            .orElse(null);
        if (notification == null) {
            log.warn("Notification {} not found for outbox {}", outbox.getNotificationId(), outbox.getId());
            outbox.setStatus(DeliveryStatus.FAILED);
            outbox.setLastError("Notification not found");
            outboxRepository.save(outbox);
            return;
        }

        try {
            deliverNotification(notification, outbox.getChannel());
            
            // Success: mark outbox as sent
            outbox.setStatus(DeliveryStatus.SENT);
            outbox.setDeliveredAt(Instant.now());
            notification.setDeliveryStatus(DeliveryStatus.SENT);
            notificationRepository.save(notification);
            outboxRepository.save(outbox);
            log.info("Delivered notification {} via {}", notification.getId(), outbox.getChannel());
        } catch (Exception e) {
            log.error("Failed to deliver notification {} via {}", notification.getId(), outbox.getChannel(), e);
            
            // Increment retry count and schedule next attempt
            outbox.setRetryCount(outbox.getRetryCount() + 1);
            outbox.setLastError(e.getMessage() != null ? e.getMessage().substring(0, Math.min(1000, e.getMessage().length())) : "Unknown error");
            
            if (outbox.getRetryCount() >= outbox.getMaxRetries()) {
                // Max retries exceeded
                outbox.setStatus(DeliveryStatus.FAILED);
                notification.setDeliveryStatus(DeliveryStatus.FAILED);
                notificationRepository.save(notification);
                log.warn("Max retries exceeded for notification {}", notification.getId());
            } else {
                // Exponential backoff: 2^retryCount minutes
                long backoffMinutes = (long) Math.pow(2, outbox.getRetryCount());
                outbox.setNextRetryAt(Instant.now().plus(Duration.ofMinutes(backoffMinutes)));
                outbox.setStatus(DeliveryStatus.FAILED);
            }
            
            outboxRepository.save(outbox);
        }
    }

    private void deliverNotification(Notification notification, NotificationChannel channel) {
        switch (channel) {
            case EMAIL:
                String recipientEmail = userServiceClient.getUserEmail(notification.getUserId());
                sesEmailService.sendEmail(recipientEmail, notification.getTitle(), notification.getBody());
                break;
            case WHATSAPP:
                // TODO: Integrate WhatsApp provider
                log.info("WhatsApp delivery stub for user {}", notification.getUserId());
                break;
            default:
                throw new IllegalArgumentException("Unsupported channel: " + channel);
        }
    }
}
