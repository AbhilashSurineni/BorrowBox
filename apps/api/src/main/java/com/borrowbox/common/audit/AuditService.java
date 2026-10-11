package com.borrowbox.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Audit trail for admin writes (and automatic actions, with a null actor). */
@Service
public class AuditService {
  private final AuditLogRepository repo;
  private final ObjectMapper mapper;

  public AuditService(AuditLogRepository repo, ObjectMapper mapper) {
    this.repo = repo;
    this.mapper = mapper;
  }

  public void log(String actorId, String action, String entity, String entityId, Map<String, ?> metadata) {
    AuditLog a = new AuditLog();
    a.actorId = actorId;
    a.action = action;
    a.entity = entity;
    a.entityId = entityId;
    try {
      a.metadata = metadata == null ? null : mapper.writeValueAsString(metadata);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
    repo.save(a);
  }
}
