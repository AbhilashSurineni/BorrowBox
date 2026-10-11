package com.borrowbox.notifications;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, String> {
  /**
   * Inserts unless a row with the same dedupe_key exists (MySQL stand-in for Prisma's
   * createMany skipDuplicates). Returns 1 when inserted, 0 when it was a duplicate.
   */
  @Modifying
  @Query(
      value =
          """
          INSERT INTO notifications (id, user_id, type, title, body, link, is_read, dedupe_key, created_at)
          VALUES (:id, :userId, :type, :title, :body, :link, 0, :dedupeKey, :createdAt)
          ON DUPLICATE KEY UPDATE id = id
          """,
      nativeQuery = true)
  int insertIgnoringDuplicate(
      @Param("id") String id,
      @Param("userId") String userId,
      @Param("type") String type,
      @Param("title") String title,
      @Param("body") String body,
      @Param("link") String link,
      @Param("dedupeKey") String dedupeKey,
      @Param("createdAt") Instant createdAt);

  Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

  Page<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(String userId, Pageable pageable);

  long countByUserIdAndIsReadFalse(String userId);

  @Modifying
  @Query("update Notification n set n.isRead = true where n.id = :id and n.userId = :userId")
  int markRead(@Param("id") String id, @Param("userId") String userId);

  @Modifying
  @Query("update Notification n set n.isRead = true where n.userId = :userId and n.isRead = false")
  int markAllRead(@Param("userId") String userId);
}
