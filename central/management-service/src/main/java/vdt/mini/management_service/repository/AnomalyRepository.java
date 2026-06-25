package vdt.mini.management_service.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import vdt.mini.management_service.entity.AnomalyDocument;

public interface AnomalyRepository extends ElasticsearchRepository<AnomalyDocument, String> {
}
