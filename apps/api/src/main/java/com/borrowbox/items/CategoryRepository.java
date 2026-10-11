package com.borrowbox.items;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, String> {
  List<Category> findAllByOrderByNameAsc();

  List<Category> findByIsActiveTrueOrderByNameAsc();

  List<Category> findByIdIn(Collection<String> ids);

  Optional<Category> findByName(String name);

  /** Case-insensitive name lookup (the column collation is case-insensitive). */
  @Query("select c from Category c where lower(c.name) = lower(:name) and (:exceptId is null or c.id <> :exceptId)")
  List<Category> findByNameIgnoreCaseExcept(@Param("name") String name, @Param("exceptId") String exceptId);
}
