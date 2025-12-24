package dopaminelite.notifications.repository;

import dopaminelite.notifications.entity.NotificationTemplate;
import dopaminelite.notifications.entity.enums.TemplateType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for notification templates.
 */
@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
    
    /**
     * Find template by templateId (display identifier).
     */
    Optional<NotificationTemplate> findByTemplateId(String templateId);
    
    /**
     * Find templates by type with pagination.
     */
    Page<NotificationTemplate> findByType(TemplateType type, Pageable pageable);
    
    /**
     * Search templates by name or ID.
     */
    @Query("SELECT t FROM NotificationTemplate t WHERE " +
           "LOWER(t.templateName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.templateId) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<NotificationTemplate> search(@Param("search") String search, Pageable pageable);
    
    /**
     * Search templates by type and search string.
     */
    @Query("SELECT t FROM NotificationTemplate t WHERE t.type = :type AND " +
           "(LOWER(t.templateName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.templateId) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<NotificationTemplate> searchByType(@Param("type") TemplateType type, 
                                           @Param("search") String search, 
                                           Pageable pageable);
}
