package com.borrowbox.items;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, String>, JpaSpecificationExecutor<Item> {
  /** SELECT ... FOR UPDATE on the item row: serialises every loan write for one item. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Item i where i.id = :id")
  Optional<Item> lockById(@Param("id") String id);

  List<Item> findByIdIn(Collection<String> ids);

  List<Item> findByOwnerId(String ownerId);

  long countByOwnerIdAndIsRemovedFalseAndDeletedAtIsNull(String ownerId);

  long countByIsRemovedFalseAndDeletedAtIsNullAndIsPausedFalse();

  long countByCategoryId(String categoryId);

  @Query("select i.categoryId, count(i) from Item i where i.isRemoved = false and i.deletedAt is null and i.isPaused = false group by i.categoryId")
  List<Object[]> countVisibleByCategory();

  @Query("select i.categoryId, count(i) from Item i group by i.categoryId")
  List<Object[]> countAllByCategory();

  @Query("select i.ownerId, count(i) from Item i where i.ownerId in :ownerIds group by i.ownerId")
  List<Object[]> countByOwners(@Param("ownerIds") Collection<String> ownerIds);

  @Query(
      """
      select i from Item i where i.ownerId = :ownerId and i.deletedAt is null
        and (:status <> 'listed' or (i.isPaused = false and i.isRemoved = false))
        and (:status <> 'paused' or i.isPaused = true)
      """)
  Page<Item> findMine(@Param("ownerId") String ownerId, @Param("status") String status, Pageable pageable);

  @Modifying
  @Query("update Item i set i.deletedAt = CURRENT_TIMESTAMP, i.isPaused = true where i.ownerId = :ownerId and i.deletedAt is null")
  int softDeleteByOwner(@Param("ownerId") String ownerId);

  @Query(
      """
      select i from Item i where i.deletedAt is null
        and (:removed is null or i.isRemoved = :removed)
        and (:category is null or i.categoryId = :category)
        and (:ownerId is null or i.ownerId = :ownerId)
        and (:q is null or lower(i.name) like :q escape '\\' or lower(i.description) like :q escape '\\')
      order by i.createdAt desc
      """)
  Page<Item> adminSearch(
      @Param("q") String q,
      @Param("category") String category,
      @Param("removed") Boolean removed,
      @Param("ownerId") String ownerId,
      Pageable pageable);
}
