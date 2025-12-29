package dopaminelite.notifications.entity;

import dopaminelite.notifications.entity.enums.DeliveryStatus;
import dopaminelite.notifications.entity.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Notification entity.
 * Stores per-user notifications across multiple channels.
 * Partitioning is handled at the database level via Liquibase.
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_user_channel", columnList = "user_id,channel"),
    @Index(name = "idx_user_is_read", columnList = "user_id,is_read"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_broadcast_id", columnList = "broadcast_id")
})
@Getter
@Setter
public class Notification extends AuditableEntity {
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "broadcast_id")
    private UUID broadcastId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;
    
    @Column(nullable = false, length = 500)
    private String title;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;
    
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;
    
    @Column(name = "read_at")
    private Instant readAt;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;
    
    @Column(name = "template_key", length = 100)
    private String templateKey;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
