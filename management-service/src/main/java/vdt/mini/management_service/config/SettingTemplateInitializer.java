package vdt.mini.management_service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import vdt.mini.management_service.service.SettingTemplateService;

@Component
@RequiredArgsConstructor
public class SettingTemplateInitializer implements ApplicationRunner {
    private final SettingTemplateService settingTemplateService;

    @Override
    public void run(ApplicationArguments args) {
        settingTemplateService.getGlobalTemplate();
        settingTemplateService.ensureServiceTemplatesForExistingServices();
    }
}
