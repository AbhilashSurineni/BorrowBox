package com.borrowbox.reports;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, String> {
  long countByReporterIdAndTargetTypeAndTargetIdAndStatusIn(
      String reporterId, ReportTargetType targetType, String targetId, Collection<ReportStatus> statuses);

  long countByTargetUserIdAndStatus(String targetUserId, ReportStatus status);

  long countByStatusIn(Collection<ReportStatus> statuses);

  @Query("select r.targetUserId, count(r) from Report r where r.targetUserId in :ids and r.status in :statuses group by r.targetUserId")
  List<Object[]> countByTargetUsers(@Param("ids") Collection<String> ids, @Param("statuses") Collection<ReportStatus> statuses);

  /** Report queue: OPEN, UNDER_REVIEW, RESOLVED, DISMISSED order (the PostgreSQL enum order), oldest first. */
  @Query(
      value =
          """
          select r from Report r
          where (:status is null or r.status = :status) and (:targetType is null or r.targetType = :targetType)
          order by case r.status
            when com.borrowbox.reports.ReportStatus.OPEN then 0
            when com.borrowbox.reports.ReportStatus.UNDER_REVIEW then 1
            when com.borrowbox.reports.ReportStatus.RESOLVED then 2
            else 3 end asc, r.createdAt asc
          """,
      countQuery =
          "select count(r) from Report r where (:status is null or r.status = :status) and (:targetType is null or r.targetType = :targetType)")
  Page<Report> findQueue(@Param("status") ReportStatus status, @Param("targetType") ReportTargetType targetType, Pageable pageable);
}
