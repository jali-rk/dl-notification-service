package dopaminelite.notifications.integration;

import dopaminelite.notifications.entity.Notification;
import dopaminelite.notifications.entity.enums.DeliveryStatus;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import dopaminelite.notifications.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for NotificationRepository using Testcontainers Postgres.
 *
 * Purpose:
 * - Validate Liquibase migrations apply cleanly to a fresh Postgres instance.
 * - Test repository query methods (filters, pagination, counts) against real DB.
 *
 * Notes:
 * - Requires Docker to run Testcontainers.
 * - Spring Boot automatically applies Liquibase migrations when context starts.
 */
@Testcontainers
@SpringBootTest
class NotificationRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    @DisplayName("Should apply Liquibase migrations and create notifications table")
    void migrationsApply() {
        // If we get here, Liquibase ran successfully
        assertThat(notificationRepository).isNotNull();
    }

    @Test
    @DisplayName("Should save and retrieve notification")
    void saveAndRetrieve() {
        UUID userId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setTitle("Test Title");
        notification.setBody("Test Body");
        notification.setDeliveryStatus(DeliveryStatus.PENDING);
        notification.setRead(false);

        Notification saved = notificationRepository.save(notification);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Notification found = notificationRepository.findById(saved.getId()).orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("Test Title");
    }

    @Test
    @DisplayName("Should filter by userId and channel")
    void filterByUserIdAndChannel() {
        UUID userId = UUID.randomUUID();
        
        // Create email notification
        Notification email = new Notification();
        email.setUserId(userId);
        email.setChannel(NotificationChannel.EMAIL);
        email.setTitle("Email");
        email.setBody("Body");
        email.setDeliveryStatus(DeliveryStatus.SENT);
        email.setRead(false);
        notificationRepository.save(email);

        // Create in-app notification
        Notification inApp = new Notification();
        inApp.setUserId(userId);
        inApp.setChannel(NotificationChannel.IN_APP);
        inApp.setTitle("InApp");
        inApp.setBody("Body");
        inApp.setDeliveryStatus(DeliveryStatus.SENT);
        inApp.setRead(false);
        notificationRepository.save(inApp);

        Page<Notification> emailPage = notificationRepository.findByUserIdAndChannelOrderByCreatedAtDesc(
            userId, NotificationChannel.EMAIL, PageRequest.of(0, 10)
        );

        assertThat(emailPage.getTotalElements()).isEqualTo(1);
        assertThat(emailPage.getContent().get(0).getChannel()).isEqualTo(NotificationChannel.EMAIL);
    }

    @Test
    @DisplayName("Should count unread notifications")
    void countUnread() {
        UUID userId = UUID.randomUUID();
        
        Notification read = new Notification();
        read.setUserId(userId);
        read.setChannel(NotificationChannel.IN_APP);
        read.setTitle("Read");
        read.setBody("Body");
        read.setDeliveryStatus(DeliveryStatus.SENT);
        read.setRead(true);
        notificationRepository.save(read);

        Notification unread = new Notification();
        unread.setUserId(userId);
        unread.setChannel(NotificationChannel.IN_APP);
        unread.setTitle("Unread");
        unread.setBody("Body");
        unread.setDeliveryStatus(DeliveryStatus.SENT);
        unread.setRead(false);
        notificationRepository.save(unread);

        long count = notificationRepository.countByUserIdAndIsRead(userId, false);
        assertThat(count).isEqualTo(1);
    }
}
