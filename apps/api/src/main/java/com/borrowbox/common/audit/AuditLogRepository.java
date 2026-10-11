package com.borrowbox.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
  @Query("select a from AuditLog a where (:entity is null or a.entity = :entity) order by a.createdAt desc")
  Page<AuditLog> search(@Param("entity") String entity, Pageable pageable);

  long countByActionAndEntityId(String action, String entityId);
}
