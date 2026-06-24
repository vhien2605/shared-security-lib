package vdt.mini.management_service.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vdt.mini.management_service.entity.InAppNotification;

import java.util.List;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, String> {
    List<InAppNotification> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByReadAtIsNull();
}
