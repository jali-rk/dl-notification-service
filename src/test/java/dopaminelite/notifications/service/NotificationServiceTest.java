package dopaminelite.notifications.service;

import dopaminelite.notifications.client.UserServiceClient;
import dopaminelite.notifications.dto.DirectNotificationSendRequest;
import dopaminelite.notifications.dto.NotificationEventRequest;
import dopaminelite.notifications.entity.*;
import dopaminelite.notifications.repository.DeliveryOutboxRepository;
import dopaminelite.notifications.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationService business logic.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private DeliveryOutboxRepository deliveryOutboxRepository;

    @Mock
    private SesEmailService sesEmailService;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private NotificationService notificationService;

    private UUID userId;
    private Notification notification;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUserId(userId);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setTitle("Test Title");
        notification.setBody("Test Body");
        notification.setDeliveryStatus(DeliveryStatus.PENDING);
        notification.setRead(false);
    }

    @Test
    @DisplayName("Should mark notification as read and set readAt timestamp")
    void markAsRead() {
        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new dopaminelite.notifications.dto.NotificationReadUpdateRequest();
        request.setRead(true);

        var result = notificationService.markAsRead(notification.getId(), request);

        assertThat(result.isRead()).isTrue();
        assertThat(result.getReadAt()).isNotNull();
        verify(notificationRepository).save(argThat(n -> n.isRead() && n.getReadAt() != null));
    }

    @Test
    @DisplayName("Should enqueue outbox entry for EMAIL notification event")
    void processNotificationEventEnqueuesOutbox() {
        NotificationEventRequest request = new NotificationEventRequest();
        request.setPrimaryUserId(userId);
        request.setEventType(NotificationEventType.STUDENT_VERIFIED);
        request.setChannels(List.of(NotificationChannel.EMAIL));

        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryOutboxRepository.save(any(DeliveryOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userServiceClient.getUserEmail(userId)).thenReturn("user@example.com");

        notificationService.processNotificationEvent(request);

        // Verify notification created (saved twice: once on creation, once after delivery)
        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(notifCaptor.capture());
        Notification saved = notifCaptor.getAllValues().get(0);
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.EMAIL);

        // Verify outbox entry created
        ArgumentCaptor<DeliveryOutbox> outboxCaptor = ArgumentCaptor.forClass(DeliveryOutbox.class);
        verify(deliveryOutboxRepository).save(outboxCaptor.capture());
        DeliveryOutbox outbox = outboxCaptor.getValue();
        assertThat(outbox.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(outbox.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(0);
        assertThat(outbox.getMaxRetries()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should not enqueue outbox for IN_APP channel")
    void processNotificationEventInAppNoOutbox() {
        NotificationEventRequest request = new NotificationEventRequest();
        request.setPrimaryUserId(userId);
        request.setEventType(NotificationEventType.ISSUE_MESSAGE_NEW);
        request.setChannels(List.of(NotificationChannel.IN_APP));

        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.processNotificationEvent(request);

        verify(notificationRepository).save(any(Notification.class));
        verify(deliveryOutboxRepository, never()).save(any(DeliveryOutbox.class));
    }

    @Test
    @DisplayName("Should deliver EMAIL notification and update status to SENT")
    void deliverNotificationSuccess() {
        when(userServiceClient.getUserEmail(userId)).thenReturn("user@example.com");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.deliverNotification(notification);

        verify(sesEmailService).sendEmail("user@example.com", "Test Title", "Test Body");
        verify(notificationRepository).save(argThat(n -> n.getDeliveryStatus() == DeliveryStatus.SENT));
    }

    @Test
    @DisplayName("Should mark notification FAILED on delivery exception")
    void deliverNotificationFailure() {
        when(userServiceClient.getUserEmail(userId)).thenReturn("user@example.com");
        doThrow(new RuntimeException("SES error")).when(sesEmailService).sendEmail(anyString(), anyString(), anyString());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.deliverNotification(notification);

        verify(notificationRepository).save(argThat(n -> n.getDeliveryStatus() == DeliveryStatus.FAILED));
    }

    @Test
    @DisplayName("Should send direct notifications and enqueue outbox for each user+channel")
    void sendDirectNotifications() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        DirectNotificationSendRequest request = new DirectNotificationSendRequest();
        request.setTargetUserIds(List.of(user1, user2));
        request.setChannels(List.of(NotificationChannel.EMAIL, NotificationChannel.IN_APP));
        request.setTitle("Broadcast");
        request.setBody("Message");

        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryOutboxRepository.save(any(DeliveryOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userServiceClient.getUserEmail(any())).thenReturn("user@example.com");

        notificationService.sendDirectNotifications(request);

        // 2 users * 2 channels = 4 notifications created + 2 EMAIL delivery saves = 6 total
        verify(notificationRepository, times(6)).save(any(Notification.class));
        // Only EMAIL channel enqueues (2 users * 1 EMAIL = 2)
        verify(deliveryOutboxRepository, times(2)).save(any(DeliveryOutbox.class));
    }
}
