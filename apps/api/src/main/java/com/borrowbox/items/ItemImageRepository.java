package com.borrowbox.items;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemImageRepository extends JpaRepository<ItemImage, String> {
  List<ItemImage> findByItemIdOrderByPositionAsc(String itemId);

  List<ItemImage> findByItemIdInOrderByPositionAsc(Collection<String> itemIds);

  /** First image (lowest position) per item, for cover thumbnails. */
  @Query(
      value =
          """
          SELECT im.item_id, im.url FROM item_images im
          WHERE im.item_id IN (:itemIds)
            AND im.position = (SELECT MIN(x.position) FROM item_images x WHERE x.item_id = im.item_id)
          """,
      nativeQuery = true)
  List<Object[]> findCovers(@Param("itemIds") Collection<String> itemIds);
}
