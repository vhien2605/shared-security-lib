package vdt.mini.management_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vdt.mini.management_service.dto.request.AnomalySearchRequest;
import vdt.mini.management_service.dto.response.AnomalyDetailResponse;
import vdt.mini.management_service.dto.response.AnomalyPageResponse;
import vdt.mini.management_service.dto.response.AnomalyStatisticsResponse;
import vdt.mini.management_service.dto.response.ApiSuccessResponse;
import vdt.mini.management_service.service.AnomalyQueryService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/central/api/admin/anomalies")
public class AnomalyController {
    private final AnomalyQueryService anomalyQueryService;

    @GetMapping
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<AnomalyPageResponse> search(@ModelAttribute AnomalySearchRequest request) {
        return ApiSuccessResponse.<AnomalyPageResponse>builder()
                .status(200)
                .message("OK")
                .data(anomalyQueryService.search(request))
                .build();
    }

    @GetMapping("/{anomalyId}")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<AnomalyDetailResponse> detail(@PathVariable String anomalyId) {
        return ApiSuccessResponse.<AnomalyDetailResponse>builder()
                .status(200)
                .message("OK")
                .data(anomalyQueryService.getDetail(anomalyId))
                .build();
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('admin')")
    public ApiSuccessResponse<AnomalyStatisticsResponse> statistics(@ModelAttribute AnomalySearchRequest request) {
        return ApiSuccessResponse.<AnomalyStatisticsResponse>builder()
                .status(200)
                .message("OK")
                .data(anomalyQueryService.getStatistics(request))
                .build();
    }
}
