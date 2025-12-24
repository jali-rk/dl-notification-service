package dopaminelite.notifications.entity.enums;

/**
 * Notification template types.
 */
public enum TemplateType {
    /**
     * General template - same message for all recipients.
     */
    GENERAL,
    
    /**
     * Personalized template - supports placeholders like {{month}}, {{studentName}}.
     */
    PERSONALIZED
}
