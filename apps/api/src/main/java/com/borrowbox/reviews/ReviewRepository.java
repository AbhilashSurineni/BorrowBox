package com.borrowbox.reviews;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, String> {
  Optional<Review> findByLoanIdAndReviewerId(String loanId, String reviewerId);

  List<Review> findByLoanIdIn(Collection<String> loanIds);

  List<Review> findByLoanId(String loanId);

  Page<Review> findByRevieweeIdOrderByCreatedAtDesc(String revieweeId, Pageable pageable);

  long countByRevieweeId(String revieweeId);

  @Query("select coalesce(avg(r.rating), 0), count(r) from Review r where r.revieweeId = :userId")
  List<Object[]> aggregateForUser(@Param("userId") String userId);

  /** Item rating: borrowers' reviews of the owner on loans of this item. */
  @Query(
      """
      select coalesce(avg(r.rating), 0), count(r) from Review r join com.borrowbox.loans.Loan l on l.id = r.loanId
      where r.revieweeId = :ownerId and l.itemId = :itemId
      """)
  List<Object[]> aggregateForItem(@Param("itemId") String itemId, @Param("ownerId") String ownerId);

  @Query(
      """
      select r from Review r join com.borrowbox.loans.Loan l on l.id = r.loanId
      where l.itemId = :itemId and r.revieweeId = :ownerId order by r.createdAt desc
      """)
  List<Review> findForItem(@Param("itemId") String itemId, @Param("ownerId") String ownerId, Pageable pageable);
}
