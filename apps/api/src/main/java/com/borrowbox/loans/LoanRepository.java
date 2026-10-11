package com.borrowbox.loans;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepository extends JpaRepository<Loan, String> {

  @Query(value = "SELECT item_id FROM loans WHERE id = :id", nativeQuery = true)
  String findItemIdById(@Param("id") String id);

  List<Loan> findByIdIn(Collection<String> ids);

  long countByBorrowerIdAndStatus(String borrowerId, LoanStatus status);

  long countByBorrowerIdAndStatusIn(String borrowerId, Collection<LoanStatus> statuses);

  long countByItemIdAndStatusIn(String itemId, Collection<LoanStatus> statuses);

  long countByStatus(LoanStatus status);

  long countByStatusIn(Collection<LoanStatus> statuses);

  /**
   * Rule 3 application-level check: does [start, end] overlap an APPROVED/ACTIVE/OVERDUE loan of
   * the item? Overdue loans block until today (they have not been returned yet).
   */
  @Query(
      value =
          """
          SELECT COUNT(*) FROM loans l
          WHERE l.item_id = :itemId
            AND l.status IN ('APPROVED', 'ACTIVE', 'OVERDUE')
            AND (:excludeId IS NULL OR l.id <> :excludeId)
            AND l.start_date <= :endDate
            AND (CASE WHEN l.status = 'OVERDUE' THEN GREATEST(l.end_date, :today) ELSE l.end_date END) >= :startDate
          """,
      nativeQuery = true)
  long countBlockingOverlaps(
      @Param("itemId") String itemId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("excludeId") String excludeId,
      @Param("today") LocalDate today);

  @Query(
      """
      select count(l) from Loan l where l.itemId = :itemId and l.borrowerId = :borrowerId
        and l.status = com.borrowbox.loans.LoanStatus.PENDING
        and l.startDate <= :endDate and l.endDate >= :startDate
      """)
  long countDuplicatePending(
      @Param("itemId") String itemId,
      @Param("borrowerId") String borrowerId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  @Query(
      """
      select l from Loan l where l.itemId = :itemId and l.id <> :exceptId
        and l.status = com.borrowbox.loans.LoanStatus.PENDING
        and l.startDate <= :endDate and l.endDate >= :startDate
      """)
  List<Loan> findOverlappingPending(
      @Param("itemId") String itemId,
      @Param("exceptId") String exceptId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  List<Loan> findByItemIdAndStatus(String itemId, LoanStatus status);

  List<Loan> findByBorrowerIdAndStatus(String borrowerId, LoanStatus status);

  /** Loans that block the calendar from today on (overdue ones always do). */
  @Query(
      """
      select l from Loan l where l.itemId in :itemIds
        and l.status in (com.borrowbox.loans.LoanStatus.APPROVED, com.borrowbox.loans.LoanStatus.ACTIVE, com.borrowbox.loans.LoanStatus.OVERDUE)
        and (l.endDate >= :today or l.status = com.borrowbox.loans.LoanStatus.OVERDUE)
      """)
  List<Loan> findRelevantBlocking(@Param("itemIds") Collection<String> itemIds, @Param("today") LocalDate today);

  /** Relevant blocking loans plus pending requests, for "My items" (ordered by start date). */
  @Query(
      """
      select l from Loan l where l.itemId in :itemIds
        and (l.status = com.borrowbox.loans.LoanStatus.PENDING
          or (l.status in (com.borrowbox.loans.LoanStatus.APPROVED, com.borrowbox.loans.LoanStatus.ACTIVE, com.borrowbox.loans.LoanStatus.OVERDUE)
              and (l.endDate >= :today or l.status = com.borrowbox.loans.LoanStatus.OVERDUE)))
      order by l.startDate asc
      """)
  List<Loan> findUpcomingForItems(@Param("itemIds") Collection<String> itemIds, @Param("today") LocalDate today);

  /** Guarded bulk transition (the TS updateMany ... WHERE status = from). Null patch values leave columns unchanged. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update Loan l set l.status = :to,
        l.rejectionReason = coalesce(:rejectionReason, l.rejectionReason),
        l.cancellationReason = coalesce(:cancellationReason, l.cancellationReason),
        l.cancelledById = coalesce(:cancelledById, l.cancelledById),
        l.completedAt = coalesce(:completedAt, l.completedAt),
        l.version = l.version + 1,
        l.updatedAt = :now
      where l.id in :ids and l.status = :from
      """)
  int bulkTransition(
      @Param("ids") Collection<String> ids,
      @Param("from") LoanStatus from,
      @Param("to") LoanStatus to,
      @Param("rejectionReason") String rejectionReason,
      @Param("cancellationReason") String cancellationReason,
      @Param("cancelledById") String cancelledById,
      @Param("completedAt") Instant completedAt,
      @Param("now") Instant now);

  @Query("select l.id from Loan l where l.id in :ids and l.status = :status")
  List<String> findIdsWithStatus(@Param("ids") Collection<String> ids, @Param("status") LoanStatus status);

  // ---- scheduled jobs ----

  @Query(
      """
      select l from Loan l where l.status = com.borrowbox.loans.LoanStatus.PENDING
        and (l.createdAt < :cutoff or l.startDate < :today)
      """)
  List<Loan> findExpirable(@Param("cutoff") Instant cutoff, @Param("today") LocalDate today);

  List<Loan> findByStatusAndEndDateBefore(LoanStatus status, LocalDate date);

  List<Loan> findByStatusAndReturnedAtBefore(LoanStatus status, Instant cutoff);

  @Query(
      """
      select l from Loan l where (l.status = com.borrowbox.loans.LoanStatus.ACTIVE and l.endDate in :dates)
        or l.status = com.borrowbox.loans.LoanStatus.OVERDUE
      """)
  List<Loan> findDueOrOverdue(@Param("dates") Collection<LocalDate> dates);

  List<Loan> findByStatusAndStartDate(LoanStatus status, LocalDate startDate);

  // ---- listings ----

  @Query(
      value =
          """
          select l from Loan l join com.borrowbox.items.Item i on i.id = l.itemId
          where ((:role = 'borrower' and l.borrowerId = :userId)
              or (:role = 'owner' and i.ownerId = :userId)
              or (:role = 'any' and (l.borrowerId = :userId or i.ownerId = :userId)))
            and (:allStatuses = true or l.status in :statuses)
          order by l.updatedAt desc
          """,
      countQuery =
          """
          select count(l) from Loan l join com.borrowbox.items.Item i on i.id = l.itemId
          where ((:role = 'borrower' and l.borrowerId = :userId)
              or (:role = 'owner' and i.ownerId = :userId)
              or (:role = 'any' and (l.borrowerId = :userId or i.ownerId = :userId)))
            and (:allStatuses = true or l.status in :statuses)
          """)
  Page<Loan> findForUser(
      @Param("userId") String userId,
      @Param("role") String role,
      @Param("allStatuses") boolean allStatuses,
      @Param("statuses") Collection<LoanStatus> statuses,
      Pageable pageable);

  @Query(
      value =
          """
          select l from Loan l join com.borrowbox.items.Item i on i.id = l.itemId
          where (:allStatuses = true or l.status in :statuses)
            and (:itemId is null or l.itemId = :itemId)
            and (:userId is null or l.borrowerId = :userId or i.ownerId = :userId)
          order by l.endDate asc, l.createdAt desc
          """,
      countQuery =
          """
          select count(l) from Loan l join com.borrowbox.items.Item i on i.id = l.itemId
          where (:allStatuses = true or l.status in :statuses)
            and (:itemId is null or l.itemId = :itemId)
            and (:userId is null or l.borrowerId = :userId or i.ownerId = :userId)
          """)
  Page<Loan> findForAdmin(
      @Param("allStatuses") boolean allStatuses,
      @Param("statuses") Collection<LoanStatus> statuses,
      @Param("itemId") String itemId,
      @Param("userId") String userId,
      Pageable pageable);

  @Query(
      """
      select count(l) from Loan l join com.borrowbox.items.Item i on i.id = l.itemId
      where l.status in (com.borrowbox.loans.LoanStatus.APPROVED, com.borrowbox.loans.LoanStatus.ACTIVE, com.borrowbox.loans.LoanStatus.OVERDUE)
        and (l.borrowerId = :userId or i.ownerId = :userId)
      """)
  long countBlockingForUser(@Param("userId") String userId);

  @Query(
      """
      select count(l) from Loan l join com.borrowbox.items.Item i on i.id = l.itemId
      where i.ownerId = :ownerId and l.status in :statuses
      """)
  long countAsLender(@Param("ownerId") String ownerId, @Param("statuses") Collection<LoanStatus> statuses);

  @Query("select l.borrowerId, count(l) from Loan l where l.borrowerId in :ids group by l.borrowerId")
  List<Object[]> countByBorrowers(@Param("ids") Collection<String> ids);

  @Query("select l.itemId, count(l) from Loan l where l.itemId in :ids group by l.itemId")
  List<Object[]> countByItems(@Param("ids") Collection<String> ids);
}
