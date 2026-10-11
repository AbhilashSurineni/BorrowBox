package com.borrowbox.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthTokenRepository extends JpaRepository<AuthToken, String> {
  Optional<AuthToken> findByTokenHash(String tokenHash);

  @Modifying
  @Query("update AuthToken t set t.usedAt = :now where t.userId = :userId and t.type = :type and t.usedAt is null")
  int invalidateOutstanding(@Param("userId") String userId, @Param("type") AuthTokenType type, @Param("now") Instant now);

  @Modifying
  @Query("update AuthToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
  int consume(@Param("id") String id, @Param("now") Instant now);
}
