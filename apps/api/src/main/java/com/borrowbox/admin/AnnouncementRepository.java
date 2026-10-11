package com.borrowbox.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, String> {
  Page<Announcement> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
