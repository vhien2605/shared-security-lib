package vdt.mini.management_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vdt.mini.management_service.entity.Client;
import vdt.mini.management_service.util.enums.ClientStatus;

import java.time.LocalDateTime;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, String> {
    boolean existsByClientKey(String clientKey);

    Optional<Client> findByClientKey(String clientKey);

    @Query("SELECT c FROM Client c "
            + "WHERE (:status IS NULL OR c.status = :status) "
            + "AND (:keywordPattern IS NULL OR LOWER(c.name) LIKE :keywordPattern "
            + "OR LOWER(c.clientKey) LIKE :keywordPattern) ")
    Page<Client> search(@Param("keywordPattern") String keywordPattern,
                        @Param("status") ClientStatus status,
                        Pageable pageable);

    @Query("SELECT DISTINCT c FROM Client c "
            + "LEFT JOIN FETCH c.authConfigs ac "
            + "LEFT JOIN FETCH ac.service "
            + "WHERE c.id = :id")
    Optional<Client> findByIdWithAuthConfigs(@Param("id") String id);

    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query("UPDATE Client c SET c.status = :status, c.revokedAt = :revokedAt, c.revokedBy = :revokedBy WHERE c.id = :id")
    int updateStatus(@Param("id") String id,
                     @Param("status") ClientStatus status,
                     @Param("revokedAt") LocalDateTime revokedAt,
                     @Param("revokedBy") String revokedBy);
}
