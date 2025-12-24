package dopaminelite.notifications.entity;

import dopaminelite.notifications.entity.enums.NotificationChannel;
import dopaminelite.notifications.entity.enums.TemplateType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notification template entity for reusable notification templates.
 * Supports both general broadcasts and personalized notifications with placeholders.
 */
@Entity
@Table(name = "notification_templates", indexes = {
    @Index(name = "idx_template_type", columnList = "type"),
    @Index(name = "idx_created_by", columnList = "created_by"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
public class NotificationTemplate extends AuditableEntity {
    
    @Column(name = "template_id", nullable = false, unique = true, length = 100)
    private String templateId;
    
    @Column(name = "template_name", nullable = false, length = 255)
    private String templateName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TemplateType type;
    
    @Column(name = "content_sinhala", columnDefinition = "TEXT")
    private String contentSinhala;
    
    @Column(name = "content_english", columnDefinition = "TEXT")
    private String contentEnglish;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channels", columnDefinition = "jsonb")
    private List<NotificationChannel> channels;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "sent_times", nullable = false)
    private Integer sentTimes = 0;
    
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
