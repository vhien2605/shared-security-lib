package vdt.mini.management_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vdt.mini.management_service.entity.SecureService;

public interface ServiceRepository extends JpaRepository<SecureService, String> {
}
