package dopaminelite.notifications.service;

import dopaminelite.notifications.dto.DirectNotificationSendRequest;
import dopaminelite.notifications.dto.NotificationEventRequest;
import dopaminelite.notifications.dto.UserPublicDataDto;
import dopaminelite.notifications.entity.DeliveryOutbox;
import dopaminelite.notifications.entity.Notification;
import dopaminelite.notifications.entity.enums.DeliveryStatus;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import dopaminelite.notifications.entity.enums.NotificationEventType;
import dopaminelite.notifications.repository.BroadcastRecordRepository;
import dopaminelite.notifications.repository.DeliveryOutboxRepository;
import dopaminelite.notifications.repository.NotificationRepository;
import dopaminelite.notifications.repository.NotificationTemplateRepository;
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
    private EmailService sesEmailService;

    @Mock
    private NotificationTemplateRepository templateRepository;

    @Mock
    private BroadcastRecordRepository broadcastRepository;

    @Mock
    private BffClientService bffClientService;

    @InjectMocks
    private NotificationService notificationService;

    private UUID userId;
    private UserPublicDataDto userData;
    private Notification notification;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userData = UserPublicDataDto.builder()
            .fullName("Test User")
            .email("user@example.com")
            .whatsappNumber("+94771234567")
            .codeNumber("123456")
            .build();
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

        when(bffClientService.getUserPublicData(userId, null)).thenReturn(userData);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryOutboxRepository.save(any(DeliveryOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.processNotificationEvent(request, null);

        // Verify notification created (saved twice: once on creation, once after delivery)
        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(notifCaptor.capture());
        Notification saved = notifCaptor.getAllValues().get(0);
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.EMAIL);

        // Verify outbox entry created with email
        ArgumentCaptor<DeliveryOutbox> outboxCaptor = ArgumentCaptor.forClass(DeliveryOutbox.class);
        verify(deliveryOutboxRepository).save(outboxCaptor.capture());
        DeliveryOutbox outbox = outboxCaptor.getValue();
        assertThat(outbox.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(outbox.getRecipientEmail()).isEqualTo("user@example.com");
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

        when(bffClientService.getUserPublicData(userId, null)).thenReturn(userData);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.processNotificationEvent(request, null);

        verify(notificationRepository).save(any(Notification.class));
        verify(deliveryOutboxRepository, never()).save(any(DeliveryOutbox.class));
    }

    @Test
    @DisplayName("Should deliver EMAIL notification and update status to SENT")
    void deliverNotificationSuccess() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.deliverNotification(notification, "user@example.com");

        verify(sesEmailService).sendEmail("user@example.com", "Test Title", "Test Body");
        verify(notificationRepository).save(argThat(n -> n.getDeliveryStatus() == DeliveryStatus.SENT));
    }

    @Test
    @DisplayName("Should mark notification FAILED on delivery exception")
    void deliverNotificationFailure() {
        doThrow(new RuntimeException("SES error")).when(sesEmailService).sendEmail(anyString(), anyString(), anyString());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.deliverNotification(notification, "user@example.com");

        verify(notificationRepository).save(argThat(n -> n.getDeliveryStatus() == DeliveryStatus.FAILED));
    }

    @Test
    @DisplayName("Should send direct notifications and enqueue outbox for each user+channel")
    void sendDirectNotifications() {
        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();
        UserPublicDataDto user1Data = UserPublicDataDto.builder().fullName("User 1").email("user1@example.com").build();
        UserPublicDataDto user2Data = UserPublicDataDto.builder().fullName("User 2").email("user2@example.com").build();

        DirectNotificationSendRequest request = new DirectNotificationSendRequest();
        request.setTargetUserIds(List.of(user1Id, user2Id));
        request.setChannels(List.of(NotificationChannel.EMAIL, NotificationChannel.IN_APP));
        request.setTitle("Broadcast");
        request.setBody("Message");

        when(bffClientService.getUserPublicData(user1Id, null)).thenReturn(user1Data);
        when(bffClientService.getUserPublicData(user2Id, null)).thenReturn(user2Data);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryOutboxRepository.save(any(DeliveryOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(broadcastRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID sentBy = UUID.randomUUID();
        notificationService.sendDirectNotifications(request, sentBy, null);

        // 2 users * 2 channels = 4 notifications created + 2 EMAIL delivery saves = 6 total
        verify(notificationRepository, times(6)).save(any(Notification.class));
        // Only EMAIL channel enqueues (2 users * 1 EMAIL = 2)
        verify(deliveryOutboxRepository, times(2)).save(any(DeliveryOutbox.class));
    }

    // ========== SES-Specific Integration Tests ==========

    @Test
    @DisplayName("deliverNotification EMAIL channel calls SES service with correct parameters")
    void deliverNotification_emailChannel_callsSesServiceWithCorrectParameters() {
        // Arrange
        notification.setTitle("Important Update");
        notification.setBody("Your account has been verified");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        notificationService.deliverNotification(notification, "john.doe@example.com");

        // Assert
        verify(sesEmailService).sendEmail("john.doe@example.com", "Important Update", "Your account has been verified");
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    @DisplayName("deliverNotification EMAIL channel with invalid email marks as FAILED")
    void deliverNotification_emailChannel_withInvalidEmail_marksFailed() {
        // Arrange
        doThrow(new RuntimeException("Invalid email")).when(sesEmailService).sendEmail(anyString(), anyString(), anyString());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        notificationService.deliverNotification(notification, "invalid-email");

        // Assert
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    @DisplayName("deliverNotification EMAIL channel with empty title sends with empty subject")
    void deliverNotification_emailChannel_withEmptyTitle_sendsWithEmptySubject() {
        // Arrange
        notification.setTitle("");
        notification.setBody("Body content");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        notificationService.deliverNotification(notification, "user@example.com");

        // Assert
        verify(sesEmailService).sendEmail("user@example.com", "", "Body content");
        verify(notificationRepository).save(argThat(n -> n.getDeliveryStatus() == DeliveryStatus.SENT));
    }

    @Test
    @DisplayName("deliverNotification EMAIL channel with null title sends null subject")
    void deliverNotification_emailChannel_withNullTitle_sendsNullSubject() {
        // Arrange
        notification.setTitle(null);
        notification.setBody("Body content");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        notificationService.deliverNotification(notification, "user@example.com");

        // Assert
        verify(sesEmailService).sendEmail("user@example.com", null, "Body content");
        verify(notificationRepository).save(argThat(n -> n.getDeliveryStatus() == DeliveryStatus.SENT));
    }

    @Test
    @DisplayName("processNotificationEvent with STUDENT_VERIFIED sends email via SES")
    void processNotificationEvent_studentVerified_sendsEmailViaSes() {
        // Arrange
        NotificationEventRequest request = new NotificationEventRequest();
        request.setPrimaryUserId(userId);
        request.setEventType(NotificationEventType.STUDENT_VERIFIED);
        request.setChannels(List.of(NotificationChannel.EMAIL));

        when(bffClientService.getUserPublicData(userId, null)).thenReturn(userData);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryOutboxRepository.save(any(DeliveryOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        notificationService.processNotificationEvent(request, null);

        // Assert
        verify(sesEmailService).sendEmail(eq("user@example.com"), anyString(), anyString());
        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(notifCaptor.capture());

        // Verify the notification was created with correct channel
        Notification savedNotif = notifCaptor.getAllValues().get(0);
        assertThat(savedNotif.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(savedNotif.getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("processNotificationEvent with PAYMENT_STATUS_CHANGED sends custom email content")
    void processNotificationEvent_paymentStatusChanged_sendsEmailWithCustomContent() {
        // Arrange
        NotificationEventRequest request = new NotificationEventRequest();
        request.setPrimaryUserId(userId);
        request.setEventType(NotificationEventType.PAYMENT_STATUS_CHANGED);
        request.setChannels(List.of(NotificationChannel.EMAIL));
        request.setPayload(java.util.Map.of("status", "COMPLETED", "amount", "100.00"));

        when(bffClientService.getUserPublicData(userId, null)).thenReturn(userData);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryOutboxRepository.save(any(DeliveryOutbox.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        notificationService.processNotificationEvent(request, null);

        // Assert
        verify(sesEmailService).sendEmail(eq("user@example.com"), anyString(), anyString());

        // Verify notification contains event-specific content
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(captor.capture());
        Notification savedNotif = captor.getAllValues().get(0);
        assertThat(savedNotif.getTemplateKey()).isEqualTo("PAYMENT_STATUS_CHANGED");
    }

    @Test
    @DisplayName("sendDirectNotifications multiple users calls SES for each EMAIL notification")
    void sendDirectNotifications_multipleUsers_callsSesForEachUser() {
        // Arrange
        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();
        UUID user3Id = UUID.randomUUID();
        UserPublicDataDto user1Data = UserPublicDataDto.builder().fullName("User 1").email("user1@example.com").build();
        UserPublicDataDto user2Data = UserPublicDataDto.builder().fullName("User 2").email("user2@example.com").build();
        UserPublicDataDto user3Data = UserPublicDataDto.builder().fullName("User 3").email("user3@example.com").build();

        DirectNotificationSendRequest request = new DirectNotificationSendRequest();
        request.setTargetUserIds(List.of(user1Id, user2Id, user3Id));
        request.setChannels(List.of(NotificationChannel.EMAIL));
        request.setTitle("System Maintenance");
        request.setBody("Scheduled downtime at 2 AM");

        when(bffClientService.getUserPublicData(user1Id, null)).thenReturn(user1Data);
        when(bffClientService.getUserPublicData(user2Id, null)).thenReturn(user2Data);
        when(bffClientService.getUserPublicData(user3Id, null)).thenReturn(user3Data);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryOutboxRepository.save(any(DeliveryOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(broadcastRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        UUID sentBy = UUID.randomUUID();
        notificationService.sendDirectNotifications(request, sentBy, null);

        // Assert
        verify(sesEmailService).sendEmail("user1@example.com", "System Maintenance", "Scheduled downtime at 2 AM");
        verify(sesEmailService).sendEmail("user2@example.com", "System Maintenance", "Scheduled downtime at 2 AM");
        verify(sesEmailService).sendEmail("user3@example.com", "System Maintenance", "Scheduled downtime at 2 AM");
        verify(sesEmailService, times(3)).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("sendDirectNotifications with mixed channels only calls SES for EMAIL")
    void sendDirectNotifications_mixedChannels_onlyCallsSesForEmail() {
        // Arrange
        UUID user1Id = UUID.randomUUID();
        UserPublicDataDto user1Data = UserPublicDataDto.builder().fullName("User").email("user@example.com").build();

        DirectNotificationSendRequest request = new DirectNotificationSendRequest();
        request.setTargetUserIds(List.of(user1Id));
        request.setChannels(List.of(NotificationChannel.EMAIL, NotificationChannel.IN_APP, NotificationChannel.WHATSAPP));
        request.setTitle("Multi-channel Test");
        request.setBody("Testing channels");

        when(bffClientService.getUserPublicData(user1Id, null)).thenReturn(user1Data);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryOutboxRepository.save(any(DeliveryOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(broadcastRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        UUID sentBy = UUID.randomUUID();
        notificationService.sendDirectNotifications(request, sentBy, null);

        // Assert
        // SES should only be called for EMAIL channel, not for IN_APP or WHATSAPP
        verify(sesEmailService, times(1)).sendEmail("user@example.com", "Multi-channel Test", "Testing channels");

        // Verify 3 notifications created (one per channel) + 2 for delivery (EMAIL + WHATSAPP) + 2 for broadcast updates = 7 total
        verify(notificationRepository, times(5)).save(any(Notification.class));

        // Verify only EMAIL and WHATSAPP enqueued to outbox (IN_APP is immediate)
        verify(deliveryOutboxRepository, times(2)).save(any(DeliveryOutbox.class));
    }
}
