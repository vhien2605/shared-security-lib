package vdt.mini.management_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vdt.mini.management_service.entity.SettingTemplate;
import vdt.mini.management_service.util.enums.SettingTemplateLevel;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SettingTemplateRepository extends JpaRepository<SettingTemplate, String> {
    Optional<SettingTemplate> findFirstByLevelOrderByIdAsc(SettingTemplateLevel level);
    Optional<SettingTemplate> findByLevelAndSecureServiceId(SettingTemplateLevel level, String serviceId);
    List<SettingTemplate> findByLevelAndSecureServiceIdInOrderByIdAsc(SettingTemplateLevel level, Collection<String> serviceIds);
    List<SettingTemplate> findAllByLevelOrderByIdAsc(SettingTemplateLevel level);
    boolean existsByLevel(SettingTemplateLevel level);
    boolean existsByLevelAndSecureServiceId(SettingTemplateLevel level, String serviceId);
}
