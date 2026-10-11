package com.borrowbox.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Modifying
  @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
  int revokeFamily(@Param("familyId") String familyId, @Param("now") Instant now);

  @Modifying
  @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
  int revokeAllForUser(@Param("userId") String userId, @Param("now") Instant now);

  /** Guarded revoke: of two concurrent refreshes with the same token, only one rotates. */
  @Modifying
  @Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
  int revokeIfActive(@Param("id") String id, @Param("now") Instant now);
}
