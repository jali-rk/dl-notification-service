package dopaminelite.notifications.repository;

import dopaminelite.notifications.entity.Notification;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * JPA repository for notifications.
 * Query methods support common filters used by the API.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    
    /**
     * Find notifications by user ID with pagination.
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    /**
     * Find notifications by user ID and channel with pagination.
     */
    Page<Notification> findByUserIdAndChannelOrderByCreatedAtDesc(
        UUID userId, 
        NotificationChannel channel, 
        Pageable pageable
    );
    
    /**
     * Find unread notifications by user ID with pagination.
     */
    Page<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(
        UUID userId, 
        boolean isRead, 
        Pageable pageable
    );
    
    /**
     * Find unread notifications by user ID and channel with pagination.
     */
    Page<Notification> findByUserIdAndIsReadAndChannelOrderByCreatedAtDesc(
        UUID userId, 
        boolean isRead, 
        NotificationChannel channel, 
        Pageable pageable
    );
    
    /**
     * Count total notifications for a user.
     */
    long countByUserId(UUID userId);
    
    /**
     * Count unread notifications for a user.
     */
    long countByUserIdAndIsRead(UUID userId, boolean isRead);
    
    /**
     * Count notifications by user and channel.
     */
    long countByUserIdAndChannel(UUID userId, NotificationChannel channel);
    
    /**
     * Count unread notifications by user and channel.
     */
    long countByUserIdAndIsReadAndChannel(UUID userId, boolean isRead, NotificationChannel channel);

    // Admin endpoints - list all notifications without user filter

    /**
     * Find all notifications with pagination (admin).
     */
    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Find all notifications by channel with pagination (admin).
     */
    Page<Notification> findAllByChannelOrderByCreatedAtDesc(
        NotificationChannel channel,
        Pageable pageable
    );

    /**
     * Find all unread notifications with pagination (admin).
     */
    Page<Notification> findAllByIsReadOrderByCreatedAtDesc(
        boolean isRead,
        Pageable pageable
    );

    /**
     * Find all unread notifications by channel with pagination (admin).
     */
    Page<Notification> findAllByIsReadAndChannelOrderByCreatedAtDesc(
        boolean isRead,
        NotificationChannel channel,
        Pageable pageable
    );

    /**
     * Find all notifications by broadcast ID.
     */
    java.util.List<Notification> findByBroadcastId(UUID broadcastId);
}
