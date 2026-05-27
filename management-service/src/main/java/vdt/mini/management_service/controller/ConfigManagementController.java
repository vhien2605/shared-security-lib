package vdt.mini.management_service.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/central/api/configs")
public class ConfigManagementController {
    @GetMapping("/test")
    public String test(Authentication authentication) {
        return "nice";
    }
}
