package vdt.mini.management_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vdt.mini.management_service.dto.request.ClientCreateRequest;
import vdt.mini.management_service.dto.request.ClientUpdateRequest;
import vdt.mini.management_service.dto.response.ApiSuccessResponse;
import vdt.mini.management_service.dto.response.ClientCreateResponse;
import vdt.mini.management_service.dto.response.ClientDetailResponse;
import vdt.mini.management_service.dto.response.ClientListItemResponse;
import vdt.mini.management_service.dto.response.ClientUpdateResponse;
import vdt.mini.management_service.service.ClientSecurityService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/clients")
public class ClientSecurityController {
    private final ClientSecurityService clientSecurityService;

    @GetMapping
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<Page<ClientListItemResponse>> listClients(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ApiSuccessResponse.<Page<ClientListItemResponse>>builder()
                .status(200)
                .message("OK")
                .data(clientSecurityService.listClients(keyword, status, pageable))
                .build();
    }

    @GetMapping("/{clientId}")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<ClientDetailResponse> getClient(@PathVariable String clientId) {
        return ApiSuccessResponse.<ClientDetailResponse>builder()
                .status(200)
                .message("OK")
                .data(clientSecurityService.getClient(clientId))
                .build();
    }

    @PostMapping
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<ClientCreateResponse> createClient(
            @RequestBody ClientCreateRequest request,
            Authentication authentication) {
        return ApiSuccessResponse.<ClientCreateResponse>builder()
                .status(200)
                .message("OK")
                .data(clientSecurityService.createClient(request, authentication))
                .build();
    }

    @PutMapping("/{clientId}")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<ClientUpdateResponse> updateClient(
            @PathVariable String clientId,
            @RequestBody ClientUpdateRequest request,
            Authentication authentication) {
        return ApiSuccessResponse.<ClientUpdateResponse>builder()
                .status(200)
                .message("OK")
                .data(clientSecurityService.updateClient(clientId, request, authentication))
                .build();
    }
}
