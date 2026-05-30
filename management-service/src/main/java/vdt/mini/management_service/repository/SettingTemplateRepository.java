package vdt.mini.management_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vdt.mini.management_service.entity.SettingTemplate;
import vdt.mini.management_service.util.enums.SettingTemplateLevel;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SettingTemplateRepository extends JpaRepository<SettingTemplate, String> {
    Optional<SettingTemplate> findFirstByLevel(SettingTemplateLevel level);
    Optional<SettingTemplate> findByLevelAndSecureServiceId(SettingTemplateLevel level, String serviceId);
    List<SettingTemplate> findByLevelAndSecureServiceIdIn(SettingTemplateLevel level, Collection<String> serviceIds);
    List<SettingTemplate> findAllByLevel(SettingTemplateLevel level);
    boolean existsByLevel(SettingTemplateLevel level);
    boolean existsByLevelAndSecureServiceId(SettingTemplateLevel level, String serviceId);
}
