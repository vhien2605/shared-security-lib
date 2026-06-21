package vdt.mini.management_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vdt.mini.management_service.dto.request.SecurityLogSearchRequest;
import vdt.mini.management_service.dto.response.ApiSuccessResponse;
import vdt.mini.management_service.dto.response.SecurityLogPageResponse;
import vdt.mini.management_service.service.SecurityLogQueryService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/central/api/admin/security-logs")
public class SecurityLogController {
    private final SecurityLogQueryService securityLogQueryService;

    @GetMapping
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<SecurityLogPageResponse> search(@ModelAttribute SecurityLogSearchRequest request) {
        return ApiSuccessResponse.<SecurityLogPageResponse>builder()
                .status(200)
                .message("OK")
                .data(securityLogQueryService.search(request))
                .build();
    }
}
