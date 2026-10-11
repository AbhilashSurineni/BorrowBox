package com.borrowbox.users;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {
  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  List<User> findByIdIn(Collection<String> ids);

  long countByDeletedAtIsNull();

  /** Locks the borrower's row so Rule 4 limit checks cannot be raced (MySQL stand-in for the advisory lock). */
  @Query(value = "SELECT id FROM users WHERE id = :id FOR UPDATE", nativeQuery = true)
  String lockById(@Param("id") String id);

  @Query("select u from User u where u.deletedAt is null and u.status = com.borrowbox.users.UserStatus.ACTIVE and (:role is null or u.role = :role)")
  List<User> findAnnouncementRecipients(@Param("role") Role role);


  @Query(
      """
      select u from User u where u.deletedAt is null
        and (:status is null or u.status = :status)
        and (:role is null or u.role = :role)
        and (:q is null or lower(u.name) like :q escape '\\' or lower(u.email) like :q escape '\\' or lower(u.department) like :q escape '\\')
      order by u.createdAt desc
      """)
  Page<User> adminSearch(@Param("q") String q, @Param("status") UserStatus status, @Param("role") Role role, Pageable pageable);
}
