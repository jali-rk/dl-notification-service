package dopaminelite.notifications.repository;

import dopaminelite.notifications.entity.BroadcastRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA repository for broadcast records.
 */
@Repository
public interface BroadcastRecordRepository extends JpaRepository<BroadcastRecord, UUID> {
    
    /**
     * Find broadcasts by sentBy user.
     */
    Page<BroadcastRecord> findBySentBy(UUID sentBy, Pageable pageable);
    
    /**
     * Find broadcasts sent within a date range.
     */
    Page<BroadcastRecord> findBySentAtBetween(Instant dateFrom, Instant dateTo, Pageable pageable);
    
    /**
     * Search broadcasts by title or body content.
     */
    @Query("SELECT b FROM BroadcastRecord b WHERE " +
           "LOWER(b.title) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<BroadcastRecord> search(@Param("search") String search, Pageable pageable);
    
    /**
     * Complex search with multiple filters.
     * Uses native SQL to avoid Hibernate 6 UUID mapping issues with PostgreSQL.
     */
    @Query(value = "SELECT b.* " +
           "FROM broadcast_records b WHERE " +
           "(CAST(:sentBy AS uuid) IS NULL OR b.sent_by = CAST(:sentBy AS uuid)) AND " +
           "(CAST(:dateFrom AS timestamp) IS NULL OR b.sent_at >= CAST(:dateFrom AS timestamp)) AND " +
           "(CAST(:dateTo AS timestamp) IS NULL OR b.sent_at <= CAST(:dateTo AS timestamp)) AND " +
           "(:search IS NULL OR LOWER(b.title) LIKE LOWER(CONCAT('%', :search, '%')))",
           countQuery = "SELECT COUNT(*) FROM broadcast_records b WHERE " +
           "(CAST(:sentBy AS uuid) IS NULL OR b.sent_by = CAST(:sentBy AS uuid)) AND " +
           "(CAST(:dateFrom AS timestamp) IS NULL OR b.sent_at >= CAST(:dateFrom AS timestamp)) AND " +
           "(CAST(:dateTo AS timestamp) IS NULL OR b.sent_at <= CAST(:dateTo AS timestamp)) AND " +
           "(:search IS NULL OR LOWER(b.title) LIKE LOWER(CONCAT('%', :search, '%')))",
           nativeQuery = true)
    Page<BroadcastRecord> findByFilters(@Param("sentBy") UUID sentBy,
                                        @Param("dateFrom") Instant dateFrom,
                                        @Param("dateTo") Instant dateTo,
                                        @Param("search") String search,
                                        Pageable pageable);
}
