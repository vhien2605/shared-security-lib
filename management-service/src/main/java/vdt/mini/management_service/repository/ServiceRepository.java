package vdt.mini.management_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vdt.mini.management_service.entity.SecureService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface ServiceRepository extends JpaRepository<SecureService, String> {

    @Query("SELECT ie.secureService.id, COUNT(ie) FROM InboundEndpoint ie WHERE ie.secureService.id IN :ids GROUP BY ie.secureService.id")
    List<Object[]> countInboundsByServiceIdsRaw(@Param("ids") List<String> ids);

    @Query("SELECT oe.secureService.id, COUNT(oe) FROM OutboundEndpoint oe WHERE oe.secureService.id IN :ids GROUP BY oe.secureService.id")
    List<Object[]> countOutboundsByServiceIdsRaw(@Param("ids") List<String> ids);

    default Map<String, Long> countInboundsByServiceIds(List<String> ids) {
        return countInboundsByServiceIdsRaw(ids).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
    }

    default Map<String, Long> countOutboundsByServiceIds(List<String> ids) {
        return countOutboundsByServiceIdsRaw(ids).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
    }
}
