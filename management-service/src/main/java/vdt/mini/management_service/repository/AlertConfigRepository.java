package vdt.mini.management_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vdt.mini.management_service.entity.AlertConfig;

public interface AlertConfigRepository extends JpaRepository<AlertConfig, String> {
}
