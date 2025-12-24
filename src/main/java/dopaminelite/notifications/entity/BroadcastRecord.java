package dopaminelite.notifications.entity;

import dopaminelite.notifications.entity.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Broadcast record entity to track broadcast notification send operations.
 * Links to individual notification records created for each recipient.
 */
@Entity
@Table(name = "broadcast_records", indexes = {
    @Index(name = "idx_sent_by", columnList = "sent_by"),
    @Index(name = "idx_sent_at", columnList = "sent_at"),
    @Index(name = "idx_template_id", columnList = "template_id")
})
@Getter
@Setter
public class BroadcastRecord extends BaseEntity {
    
    @Column(name = "template_id")
    private UUID templateId;
    
    @Column(name = "title", nullable = false, length = 500)
    private String title;
    
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channels", columnDefinition = "jsonb", nullable = false)
    private List<NotificationChannel> channels;
    
    @Column(name = "recipient_count", nullable = false)
    private Integer recipientCount = 0;
    
    @Column(name = "success_count", nullable = false)
    private Integer successCount = 0;
    
    @Column(name = "failure_count", nullable = false)
    private Integer failureCount = 0;
    
    @Column(name = "sent_by", nullable = false)
    private UUID sentBy;
    
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
