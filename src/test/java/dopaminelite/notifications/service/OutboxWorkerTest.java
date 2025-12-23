package dopaminelite.notifications.service;

import dopaminelite.notifications.client.UserServiceClient;
import dopaminelite.notifications.entity.DeliveryOutbox;
import dopaminelite.notifications.entity.Notification;
import dopaminelite.notifications.entity.enums.DeliveryStatus;
import dopaminelite.notifications.entity.enums.NotificationChannel;
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
import org.springframework.mail.MailSendException;

import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxWorkerTest {

    @Mock
    private DeliveryOutboxRepository outboxRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmailService sesEmailService;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private OutboxWorker outboxWorker;

    private Notification testNotification;
    private DeliveryOutbox testOutbox;
    private UUID notificationId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        notificationId = UUID.randomUUID();
        userId = UUID.randomUUID();

        // Create test notification
        testNotification = new Notification();
        testNotification.setId(notificationId);
        testNotification.setUserId(userId);
        testNotification.setChannel(NotificationChannel.EMAIL);
        testNotification.setTitle("Test Notification");
        testNotification.setBody("Test Body");
        testNotification.setDeliveryStatus(DeliveryStatus.PENDING);

        // Create test outbox entry
        testOutbox = new DeliveryOutbox();
        testOutbox.setId(UUID.randomUUID());
        testOutbox.setNotificationId(notificationId);
        testOutbox.setChannel(NotificationChannel.EMAIL);
        testOutbox.setStatus(DeliveryStatus.PENDING);
        testOutbox.setRetryCount(0);
        testOutbox.setMaxRetries(3);
        testOutbox.setNextRetryAt(Instant.now());
    }

    @Test
    @DisplayName("processPending with email delivery success marks as SENT")
    void processPending_emailDeliverySuccess_marksSent() {
        // Arrange
        when(outboxRepository.findByStatusInAndNextRetryAtBefore(any(), any()))
                .thenReturn(Collections.singletonList(testOutbox));
        when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(testNotification));
        when(userServiceClient.getUserEmail(userId))
                .thenReturn("user@example.com");
        doNothing().when(sesEmailService).sendEmail(anyString(), anyString(), anyString());

        // Act
        outboxWorker.processPending();

        // Assert
        ArgumentCaptor<DeliveryOutbox> outboxCaptor = ArgumentCaptor.forClass(DeliveryOutbox.class);
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);

        verify(outboxRepository).save(outboxCaptor.capture());
        verify(notificationRepository).save(notificationCaptor.capture());
        verify(sesEmailService).sendEmail("user@example.com", "Test Notification", "Test Body");

        DeliveryOutbox savedOutbox = outboxCaptor.getValue();
        Notification savedNotification = notificationCaptor.getValue();

        assertEquals(DeliveryStatus.SENT, savedOutbox.getStatus());
        assertNotNull(savedOutbox.getDeliveredAt());
        assertEquals(DeliveryStatus.SENT, savedNotification.getDeliveryStatus());
    }

    @Test
    @DisplayName("processPending with SES failure increments retry count")
    void processPending_sesFailure_incrementsRetryCount() {
        // Arrange
        when(outboxRepository.findByStatusInAndNextRetryAtBefore(any(), any()))
                .thenReturn(Collections.singletonList(testOutbox));
        when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(testNotification));
        when(userServiceClient.getUserEmail(userId))
                .thenReturn("user@example.com");
        doThrow(new MailSendException("Rate limit exceeded"))
                .when(sesEmailService).sendEmail(anyString(), anyString(), anyString());

        // Act
        outboxWorker.processPending();

        // Assert
        ArgumentCaptor<DeliveryOutbox> outboxCaptor = ArgumentCaptor.forClass(DeliveryOutbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());

        DeliveryOutbox savedOutbox = outboxCaptor.getValue();
        assertEquals(1, savedOutbox.getRetryCount());
        assertEquals(DeliveryStatus.FAILED, savedOutbox.getStatus());
        assertNotNull(savedOutbox.getLastError());
        assertTrue(savedOutbox.getLastError().contains("Rate limit exceeded"));
        assertNotNull(savedOutbox.getNextRetryAt());
        assertTrue(savedOutbox.getNextRetryAt().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("processPending with max retries exceeded marks permanently FAILED")
    void processPending_maxRetriesExceeded_marksPermanentlyFailed() {
        // Arrange
        testOutbox.setRetryCount(2); // Already failed twice
        testOutbox.setMaxRetries(3);

        when(outboxRepository.findByStatusInAndNextRetryAtBefore(any(), any()))
                .thenReturn(Collections.singletonList(testOutbox));
        when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(testNotification));
        when(userServiceClient.getUserEmail(userId))
                .thenReturn("user@example.com");
        doThrow(new MailSendException("Permanent failure"))
                .when(sesEmailService).sendEmail(anyString(), anyString(), anyString());

        // Act
        outboxWorker.processPending();

        // Assert
        ArgumentCaptor<DeliveryOutbox> outboxCaptor = ArgumentCaptor.forClass(DeliveryOutbox.class);
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);

        verify(outboxRepository).save(outboxCaptor.capture());
        verify(notificationRepository).save(notificationCaptor.capture());

        DeliveryOutbox savedOutbox = outboxCaptor.getValue();
        Notification savedNotification = notificationCaptor.getValue();

        assertEquals(3, savedOutbox.getRetryCount()); // Incremented to 3
        assertEquals(DeliveryStatus.FAILED, savedOutbox.getStatus());
        assertEquals(DeliveryStatus.FAILED, savedNotification.getDeliveryStatus());
        assertNull(savedOutbox.getDeliveredAt()); // No delivery timestamp for failed
    }

    @Test
    @DisplayName("processPending calculates exponential backoff correctly")
    void processPending_exponentialBackoff_calculatesCorrectly() {
        // Arrange
        testOutbox.setRetryCount(0);

        when(outboxRepository.findByStatusInAndNextRetryAtBefore(any(), any()))
                .thenReturn(Collections.singletonList(testOutbox));
        when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(testNotification));
        when(userServiceClient.getUserEmail(userId))
                .thenReturn("user@example.com");
        doThrow(new MailSendException("Temporary failure"))
                .when(sesEmailService).sendEmail(anyString(), anyString(), anyString());

        // Act
        outboxWorker.processPending();

        // Assert
        ArgumentCaptor<DeliveryOutbox> outboxCaptor = ArgumentCaptor.forClass(DeliveryOutbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());

        DeliveryOutbox savedOutbox = outboxCaptor.getValue();

        // After first failure: retryCount = 1, backoff = 2^1 = 2 minutes
        assertEquals(1, savedOutbox.getRetryCount());
        Instant expectedRetryTime = Instant.now().plus(Duration.ofMinutes(2));

        // Allow 1 second tolerance for test execution time
        assertTrue(savedOutbox.getNextRetryAt().isAfter(Instant.now()));
        assertTrue(savedOutbox.getNextRetryAt().isBefore(expectedRetryTime.plusSeconds(1)));
    }

    @Test
    @DisplayName("processPending with user service failure retries with fallback email")
    void processPending_userServiceFailure_retriesWithFallbackEmail() {
        // Arrange
        when(outboxRepository.findByStatusInAndNextRetryAtBefore(any(), any()))
                .thenReturn(Collections.singletonList(testOutbox));
        when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(testNotification));
        when(userServiceClient.getUserEmail(userId))
                .thenThrow(new RuntimeException("User service unavailable"));

        // Act
        outboxWorker.processPending();

        // Assert
        ArgumentCaptor<DeliveryOutbox> outboxCaptor = ArgumentCaptor.forClass(DeliveryOutbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());

        DeliveryOutbox savedOutbox = outboxCaptor.getValue();

        // Should fail and increment retry count since user service failed
        assertEquals(1, savedOutbox.getRetryCount());
        assertEquals(DeliveryStatus.FAILED, savedOutbox.getStatus());
        assertNotNull(savedOutbox.getLastError());
    }

    @Test
    @DisplayName("processPending with notification not found marks outbox FAILED")
    void processPending_notificationNotFound_marksOutboxFailed() {
        // Arrange
        when(outboxRepository.findByStatusInAndNextRetryAtBefore(any(), any()))
                .thenReturn(Collections.singletonList(testOutbox));
        when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.empty()); // Notification not found

        // Act
        outboxWorker.processPending();

        // Assert
        ArgumentCaptor<DeliveryOutbox> outboxCaptor = ArgumentCaptor.forClass(DeliveryOutbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());

        DeliveryOutbox savedOutbox = outboxCaptor.getValue();

        assertEquals(DeliveryStatus.FAILED, savedOutbox.getStatus());
        assertEquals("Notification not found", savedOutbox.getLastError());

        // Should NOT call SES service
        verify(sesEmailService, never()).sendEmail(anyString(), anyString(), anyString());
        // Should NOT save notification (doesn't exist)
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("processPending with empty pending list does nothing")
    void processPending_withEmptyList_doesNothing() {
        // Arrange
        when(outboxRepository.findByStatusInAndNextRetryAtBefore(any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        outboxWorker.processPending();

        // Assert
        verify(outboxRepository, never()).save(any());
        verify(notificationRepository, never()).save(any());
        verify(sesEmailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("processPending processes multiple outbox entries")
    void processPending_processesMultipleEntries() {
        // Arrange
        DeliveryOutbox outbox1 = createOutboxEntry();
        DeliveryOutbox outbox2 = createOutboxEntry();
        DeliveryOutbox outbox3 = createOutboxEntry();

        Notification notification1 = createNotification(outbox1.getNotificationId());
        Notification notification2 = createNotification(outbox2.getNotificationId());
        Notification notification3 = createNotification(outbox3.getNotificationId());

        when(outboxRepository.findByStatusInAndNextRetryAtBefore(any(), any()))
                .thenReturn(Arrays.asList(outbox1, outbox2, outbox3));

        when(notificationRepository.findById(outbox1.getNotificationId()))
                .thenReturn(Optional.of(notification1));
        when(notificationRepository.findById(outbox2.getNotificationId()))
                .thenReturn(Optional.of(notification2));
        when(notificationRepository.findById(outbox3.getNotificationId()))
                .thenReturn(Optional.of(notification3));

        when(userServiceClient.getUserEmail(any()))
                .thenReturn("user@example.com");

        // Act
        outboxWorker.processPending();

        // Assert
        verify(sesEmailService, times(3)).sendEmail(anyString(), anyString(), anyString());
        verify(outboxRepository, times(3)).save(any(DeliveryOutbox.class));
        verify(notificationRepository, times(3)).save(any(Notification.class));
    }

    // Helper methods
    private DeliveryOutbox createOutboxEntry() {
        DeliveryOutbox outbox = new DeliveryOutbox();
        outbox.setId(UUID.randomUUID());
        outbox.setNotificationId(UUID.randomUUID());
        outbox.setChannel(NotificationChannel.EMAIL);
        outbox.setStatus(DeliveryStatus.PENDING);
        outbox.setRetryCount(0);
        outbox.setMaxRetries(3);
        outbox.setNextRetryAt(Instant.now());
        return outbox;
    }

    private Notification createNotification(UUID id) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setUserId(UUID.randomUUID());
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setTitle("Test");
        notification.setBody("Body");
        notification.setDeliveryStatus(DeliveryStatus.PENDING);
        return notification;
    }
}
