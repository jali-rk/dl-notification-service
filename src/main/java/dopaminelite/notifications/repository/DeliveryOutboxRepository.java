package dopaminelite.notifications.repository;

import dopaminelite.notifications.entity.DeliveryOutbox;
import dopaminelite.notifications.entity.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for delivery outbox entries.
 */
@Repository
public interface DeliveryOutboxRepository extends JpaRepository<DeliveryOutbox, UUID> {

    /**
     * Find pending/failed entries ready for retry.
     */
    List<DeliveryOutbox> findByStatusInAndNextRetryAtBefore(List<DeliveryStatus> statuses, Instant now);
}
